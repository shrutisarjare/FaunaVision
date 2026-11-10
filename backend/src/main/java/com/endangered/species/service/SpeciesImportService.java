package com.endangered.species.service;

import com.endangered.species.domain.Category;
import com.endangered.species.domain.Region;
import com.endangered.species.domain.SpeciesRecord;
import com.endangered.species.repo.SpeciesRecordRepository;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Imports workbook rows into the relational catalogue. */
@Service
public class SpeciesImportService {

    public static final int[] YEARS = {2014, 2016, 2018, 2020, 2022, 2024};

    private static final Logger log = LoggerFactory.getLogger(SpeciesImportService.class);

    private final SpeciesRecordRepository records;

    public SpeciesImportService(SpeciesRecordRepository records) {
        this.records = records;
    }

    /** Replaces the whole catalogue with the contents of the workbook. */
    @Transactional
    public int replaceAll(InputStream input) throws IOException {
        Map<String, List<Map<String, String>>> sheets = SpeciesWorkbookReader.readAll(input);
        List<SpeciesRecord> parsed = parseSheets(sheets);
        records.deleteAllInBatch();
        records.saveAll(parsed);
        log.info("Imported {} species records from workbook", parsed.size());
        return parsed.size();
    }

    /** Appends every row of the uploaded sheet to one region/category. */
    @Transactional
    public int append(Region region, Category category, InputStream input) throws IOException {
        List<SpeciesRecord> parsed = new ArrayList<>();
        for (Map<String, String> row : SpeciesWorkbookReader.readFirstSheet(input)) {
            toRecord(region, category, row).ifPresent(parsed::add);
        }
        records.saveAll(parsed);
        return parsed.size();
    }

    @Transactional(readOnly = true)
    public long count() {
        return records.count();
    }

    private List<SpeciesRecord> parseSheets(Map<String, List<Map<String, String>>> sheets) {
        List<SpeciesRecord> parsed = new ArrayList<>();
        for (Map.Entry<String, List<Map<String, String>>> sheet : sheets.entrySet()) {
            String[] parts = sheet.getKey().split("_", 2);
            if (parts.length != 2) {
                continue; // e.g. all_animals, which is a redundant union sheet
            }
            Region region = Region.fromSlug(parts[0]);
            Category category = Category.fromSlug(parts[1]);
            if (region == null || category == null) {
                continue;
            }
            for (Map<String, String> row : sheet.getValue()) {
                toRecord(region, category, row).ifPresent(parsed::add);
            }
        }
        return parsed;
    }

    public static Optional<SpeciesRecord> toRecord(Region region, Category category, Map<String, String> row) {
        String name = row.get("animal_name");
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        SpeciesRecord record = new SpeciesRecord(
                region,
                category,
                truncate(name, 200),
                truncate(row.get("animal_type"), 120),
                truncate(row.get("scientific_name"), 200),
                truncate(row.get("state"), 160),
                truncate(row.get("habitat_location"), 300),
                truncate(row.get("status"), 60));
        for (int year : YEARS) {
            Integer population = parseInt(row.get("population_" + year));
            if (population != null) {
                record.addPopulation(year, population);
            }
        }
        return Optional.of(record);
    }

    static Integer parseInt(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String cleaned = value.trim().replace(",", "");
        try {
            return (int) Math.round(Double.parseDouble(cleaned));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }

    static String lower(String value) {
        return value == null ? null : value.trim().toLowerCase(Locale.ROOT);
    }
}
