package com.endangered.species.service;

import com.endangered.species.domain.Category;
import com.endangered.species.domain.PopulationEntry;
import com.endangered.species.domain.Region;
import com.endangered.species.domain.SpeciesRecord;
import com.endangered.species.repo.SpeciesRecordRepository;
import com.endangered.species.web.Dtos;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Search, drill-down and population analytics over the species catalogue. */
@Service
public class SpeciesCatalogService {

    private final SpeciesRecordRepository records;
    private final WikiImageService wiki;
    private final GeoHierarchy geo;
    private final ForecastService forecast;

    /** Conservation urgency, so a group-level sound surfaces the species that matter most. */
    private static final List<String> URGENCY =
            List.of("endangered", "declining", "vulnerable", "stable", "least concern");

    public SpeciesCatalogService(SpeciesRecordRepository records, WikiImageService wiki,
                                 GeoHierarchy geo, ForecastService forecast) {
        this.records = records;
        this.wiki = wiki;
        this.geo = geo;
        this.forecast = forecast;
    }

    @Transactional(readOnly = true)
    public List<Dtos.SpeciesSummary> search(String term) {
        if (term == null || term.isBlank()) {
            return List.of();
        }
        return toSummaries(records.search(term.trim()));
    }

    @Transactional(readOnly = true)
    public List<Dtos.SpeciesSummary> explore(String regionSlug, String categorySlug, String state, String animal) {
        Region region = Region.fromSlug(regionSlug);
        Category category = Category.fromSlug(categorySlug);
        if (region == null || category == null) {
            return List.of();
        }
        return toSummaries(records.findByRegionAndCategoryAndStateIgnoreCaseAndAnimalTypeIgnoreCaseOrderByIdAsc(
                region, category, requireLower(state), requireLower(animal)));
    }

    /**
     * Species of one catalogue type, most urgent conservation status first. A sound can
     * only name the group (an owl, a bird), so with 54 birds to choose from the candidates
     * where a match would matter most are the useful ones to surface.
     */
    @Transactional(readOnly = true)
    public List<Dtos.SpeciesSummary> byType(String type, int limit) {
        if (type == null || type.isBlank() || limit <= 0) {
            return List.of();
        }
        // One animal owns many catalogue rows, so collect by name before counting the limit.
        Map<String, SpeciesRecord> unique = new LinkedHashMap<>();
        for (SpeciesRecord record : records.findByAnimalType(type.trim(), PageRequest.of(0, 400))) {
            unique.putIfAbsent(record.getAnimalName().toLowerCase(Locale.ROOT), record);
        }
        List<SpeciesRecord> ordered = new ArrayList<>(unique.values());
        ordered.sort(Comparator.comparingInt(record -> urgency(record.getStatus())));

        List<Dtos.SpeciesSummary> summaries = new ArrayList<>(Math.min(limit, ordered.size()));
        for (SpeciesRecord record : ordered) {
            if (summaries.size() >= limit) {
                break;
            }
            summaries.add(toSummary(record));
        }
        return summaries;
    }

    private static int urgency(String status) {
        int index = status == null ? -1 : URGENCY.indexOf(status.trim().toLowerCase(Locale.ROOT));
        return index < 0 ? URGENCY.size() : index;
    }

    @Transactional(readOnly = true)
    public List<Dtos.SpeciesSummary> forSpecies(String animal) {
        return toSummaries(records.findByAnimalNameContainingIgnoreCaseOrderByIdAsc(requireLower(animal)));
    }

    /** Explore-flow analytics; returns a {@link Dtos.ErrorResponse} when nothing matches. */
    @Transactional(readOnly = true)
    public Object analysis(String regionSlug, String categorySlug, String state, String animal) {
        Region region = Region.fromSlug(regionSlug);
        Category category = Category.fromSlug(categorySlug);
        if (region == null || category == null) {
            return new Dtos.ErrorResponse("Invalid dataset");
        }
        Optional<SpeciesRecord> found =
                records.findFirstByRegionAndCategoryAndStateIgnoreCaseAndAnimalNameContainingIgnoreCaseOrderByIdAsc(
                        region, category, requireLower(state), requireLower(animal));
        if (found.isEmpty()) {
            return new Dtos.ErrorResponse("Species not found");
        }
        SpeciesRecord record = found.get();
        ForecastService.Forecast result = forecast.forecast(record.getPopulations());
        return new Dtos.Analysis(
                record.getAnimalName(),
                record.getScientificName(),
                record.getState(),
                wiki.imageFor(record.getAnimalName()),
                toPoints(record.getPopulations()),
                result.predictions(),
                result.trend(),
                result.risk());
    }

    /** Detection flow without a location filter. */
    @Transactional(readOnly = true)
    public Object detection(String animal) {
        Optional<SpeciesRecord> found =
                records.findFirstByAnimalNameContainingIgnoreCaseOrderByIdAsc(requireLower(animal));
        if (found.isEmpty()) {
            return new Dtos.ErrorResponse("Species not found");
        }
        return toDetection(found.get());
    }

    /** Detection flow filtered by state (india), country (panasia) or continent (global). */
    @Transactional(readOnly = true)
    public Object detectionAt(String animal, String regionType, String location) {
        String level = requireLower(regionType);
        String target = requireLower(location);
        List<SpeciesRecord> candidates = records.findByAnimalNameContainingIgnoreCaseOrderByIdAsc(requireLower(animal));
        if (candidates.isEmpty()) {
            return new Dtos.ErrorResponse("No data found");
        }
        Optional<SpeciesRecord> match = candidates.stream()
                .filter(record -> target.equals(resolveLocation(record, level)))
                .findFirst();
        if (match.isEmpty()) {
            return "india".equals(level) || "panasia".equals(level) || "global".equals(level)
                    ? new Dtos.ErrorResponse("No data found")
                    : new Dtos.ErrorResponse("Invalid region type");
        }
        return toDetection(match.get());
    }

    /** Distinct locations available for the drill-down selector. */
    @Transactional(readOnly = true)
    public List<String> locations(String animal, String regionType) {
        String level = requireLower(regionType);
        return records.findByAnimalNameContainingIgnoreCaseOrderByIdAsc(requireLower(animal)).stream()
                .map(record -> resolveLocation(record, level))
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .sorted()
                .toList();
    }

    /**
     * Location value a record contributes at the requested drill-down level. Records
     * from the other two region sheets return {@code null} so the dropdown only lists
     * states for India, countries for pan-Asia and continents for global.
     */
    private String resolveLocation(SpeciesRecord record, String level) {
        return switch (level) {
            case "india" -> record.getRegion() == Region.INDIA ? GeoHierarchy.normalize(record.getState()) : null;
            case "panasia" -> record.getRegion() == Region.PANASIA ? GeoHierarchy.normalize(record.getState()) : null;
            case "global" -> record.getRegion() == Region.GLOBAL ? geo.continentOf(record.getState()) : null;
            default -> null;
        };
    }

    private Dtos.Detection toDetection(SpeciesRecord record) {
        ForecastService.Forecast result = forecast.forecast(record.getPopulations());
        return new Dtos.Detection(
                record.getAnimalName(),
                record.getScientificName(),
                record.getState(),
                wiki.imageFor(record.getAnimalName()),
                result.history(),
                result.predictions(),
                result.trend(),
                result.risk());
    }

    private List<Dtos.SpeciesSummary> toSummaries(List<SpeciesRecord> source) {
        Map<String, Dtos.SpeciesSummary> unique = new LinkedHashMap<>();
        for (SpeciesRecord record : source) {
            unique.putIfAbsent(record.getAnimalName().toLowerCase(Locale.ROOT), toSummary(record));
        }
        return new ArrayList<>(unique.values());
    }

    public Dtos.SpeciesSummary toSummary(SpeciesRecord record) {
        return new Dtos.SpeciesSummary(
                record.getAnimalName(),
                record.getScientificName(),
                record.getHabitatLocation(),
                record.getStatus(),
                wiki.imageFor(record.getAnimalName()));
    }

    private static List<Dtos.PopulationPoint> toPoints(List<PopulationEntry> entries) {
        List<Dtos.PopulationPoint> points = new ArrayList<>();
        for (PopulationEntry entry : entries) {
            if (entry.getPopulation() != null) {
                points.add(new Dtos.PopulationPoint(entry.getYearValue(), entry.getPopulation()));
            }
        }
        return points;
    }

    private static String requireLower(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
