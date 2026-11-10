package com.endangered.species.service;

import com.endangered.species.domain.Category;
import com.endangered.species.domain.PopulationEntry;
import com.endangered.species.domain.Region;
import com.endangered.species.domain.SpeciesRecord;
import com.endangered.species.repo.SpeciesRecordRepository;
import com.endangered.species.web.ApiException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Admin CRUD over the catalogue; replaces the Excel read/write-back endpoints. */
@Service
public class AdminService {

    private final SpeciesRecordRepository records;
    private final SpeciesImportService importService;

    public AdminService(SpeciesRecordRepository records, SpeciesImportService importService) {
        this.records = records;
        this.importService = importService;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> allSpecies() {
        return records.findAllByOrderByIdAsc().stream().map(AdminService::toFlatRow).toList();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> sheet(String regionSlug, String categorySlug) {
        Region region = requireRegion(regionSlug);
        Category category = requireCategory(categorySlug);
        return records.findByRegionAndCategoryOrderByIdAsc(region, category).stream()
                .map(AdminService::toFlatRow)
                .toList();
    }

    @Transactional
    public Map<String, Object> replaceWorkbook(InputStream input) {
        try {
            int imported = importService.replaceAll(input);
            return Map.of("message", "Workbook imported successfully", "records", imported);
        } catch (IOException e) {
            throw ApiException.badRequest("Could not read the workbook: " + e.getMessage());
        }
    }

    @Transactional
    public Map<String, Object> appendSheet(String regionSlug, String categorySlug, InputStream input) {
        Region region = requireRegion(regionSlug);
        Category category = requireCategory(categorySlug);
        try {
            int imported = importService.append(region, category, input);
            return Map.of("message", "Uploaded successfully", "records", imported);
        } catch (IOException e) {
            throw ApiException.badRequest("Could not read the workbook: " + e.getMessage());
        }
    }

    @Transactional
    public Map<String, Object> delete(String regionSlug, String categorySlug, String animal) {
        Region region = requireRegion(regionSlug);
        Category category = requireCategory(categorySlug);
        List<SpeciesRecord> matches = records.findByRegionAndCategoryAndAnimalNameContainingIgnoreCase(
                region, category, animal == null ? "" : animal);
        if (matches.isEmpty()) {
            throw ApiException.notFound("Animal not found");
        }
        records.deleteAll(matches);
        return Map.of("message", animal + " deleted successfully", "deleted", matches.size());
    }

    @Transactional
    public Map<String, Object> update(String regionSlug, String categorySlug, String animal, Map<String, Object> body) {
        Region region = requireRegion(regionSlug);
        Category category = requireCategory(categorySlug);
        SpeciesRecord record = records
                .findByRegionAndCategoryAndAnimalNameIgnoreCase(region, category, animal == null ? "" : animal)
                .stream()
                .findFirst()
                .orElseThrow(() -> ApiException.notFound("Animal not found"));

        applyString(body, "animal_name", record::setAnimalName);
        applyString(body, "animal_type", record::setAnimalType);
        applyString(body, "scientific_name", record::setScientificName);
        applyString(body, "state", record::setState);
        applyString(body, "habitat_location", record::setHabitatLocation);
        applyString(body, "status", record::setStatus);

        for (int year : SpeciesImportService.YEARS) {
            Integer population = SpeciesImportService.parseInt(asText(body.get("population_" + year)));
            if (population != null) {
                setPopulation(record, year, population);
            }
        }

        records.save(record);
        return Map.of("message", "Updated successfully");
    }

    @Transactional
    public Map<String, Object> add(String regionSlug, String categorySlug, Map<String, Object> body) {
        Region region = requireRegion(regionSlug);
        Category category = requireCategory(categorySlug);

        Map<String, String> row = new LinkedHashMap<>();
        body.forEach((key, value) -> row.put(key, asText(value)));
        SpeciesRecord record = SpeciesImportService.toRecord(region, category, row)
                .orElseThrow(() -> ApiException.badRequest("animal_name is required"));

        records.save(record);
        return Map.of("message", "Added successfully");
    }

    private static void setPopulation(SpeciesRecord record, int year, int population) {
        Optional<PopulationEntry> existing = record.getPopulations().stream()
                .filter(entry -> entry.getYearValue() == year)
                .findFirst();
        if (existing.isPresent()) {
            existing.get().setPopulation(population);
        } else {
            record.addPopulation(year, population);
        }
    }

    private static void applyString(Map<String, Object> body, String key, java.util.function.Consumer<String> setter) {
        String value = asText(body.get(key));
        if (value != null && !value.isBlank()) {
            setter.accept(value.trim());
        }
    }

    private static String asText(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static Region requireRegion(String slug) {
        Region region = Region.fromSlug(slug);
        if (region == null) {
            throw ApiException.badRequest("Invalid region");
        }
        return region;
    }

    private static Category requireCategory(String slug) {
        Category category = Category.fromSlug(slug);
        if (category == null) {
            throw ApiException.badRequest("Invalid category");
        }
        return category;
    }

    private static Map<String, Object> toFlatRow(SpeciesRecord record) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("animal_name", orEmpty(record.getAnimalName()));
        row.put("animal_type", orEmpty(record.getAnimalType()));
        row.put("scientific_name", orEmpty(record.getScientificName()));
        row.put("state", orEmpty(record.getState()));
        row.put("habitat_location", orEmpty(record.getHabitatLocation()));
        for (int year : SpeciesImportService.YEARS) {
            row.put("population_" + year, orEmpty(populationOf(record, year)));
        }
        row.put("status", orEmpty(record.getStatus()));
        return row;
    }

    /** The legacy table was read with {@code fillna("")}; blank beats a missing column. */
    private static Object orEmpty(Object value) {
        return value == null ? "" : value;
    }

    private static Integer populationOf(SpeciesRecord record, int year) {
        List<PopulationEntry> entries = new ArrayList<>(record.getPopulations());
        for (PopulationEntry entry : entries) {
            if (entry.getYearValue() == year) {
                return entry.getPopulation();
            }
        }
        return null;
    }
}
