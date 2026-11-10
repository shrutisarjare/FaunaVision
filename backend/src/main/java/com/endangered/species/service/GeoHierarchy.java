package com.endangered.species.service;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Resolves the three geo levels used by the drill-down flows.
 *
 * <p>The workbook stores the hierarchy directly in the {@code state} column:
 * Indian states for {@code india_*}, country names for {@code panasia_*}, and
 * continent names for {@code global_*}. The original Python implementation only
 * mapped Indian states, which silently broke the pan-Asia and global filters;
 * this resolver treats a value that is already a country or continent as such.
 */
@Component
public class GeoHierarchy {

    private static final Set<String> INDIAN_STATES = Set.of(
            "andhra pradesh", "arunachal pradesh", "assam", "bihar", "chhattisgarh", "goa", "gujarat",
            "haryana", "himachal pradesh", "jharkhand", "karnataka", "kerala", "madhya pradesh",
            "maharashtra", "manipur", "meghalaya", "mizoram", "nagaland", "odisha", "punjab",
            "rajasthan", "sikkim", "tamil nadu", "telangana", "tripura", "uttar pradesh", "uttarakhand",
            "west bengal", "delhi");

    private static final Map<String, String> COUNTRY_TO_CONTINENT = Map.ofEntries(
            Map.entry("india", "asia"), Map.entry("nepal", "asia"), Map.entry("bhutan", "asia"),
            Map.entry("bangladesh", "asia"), Map.entry("china", "asia"), Map.entry("thailand", "asia"),
            Map.entry("indonesia", "asia"), Map.entry("japan", "asia"), Map.entry("sri lanka", "asia"),
            Map.entry("malaysia", "asia"), Map.entry("afghanistan", "asia"), Map.entry("armenia", "asia"),
            Map.entry("azerbaijan", "asia"), Map.entry("bahrain", "asia"), Map.entry("brunei", "asia"),
            Map.entry("cambodia", "asia"), Map.entry("cyprus", "asia"), Map.entry("georgia", "asia"),
            Map.entry("kenya", "africa"), Map.entry("south africa", "africa"), Map.entry("nigeria", "africa"),
            Map.entry("egypt", "africa"), Map.entry("germany", "europe"), Map.entry("france", "europe"),
            Map.entry("uk", "europe"), Map.entry("italy", "europe"), Map.entry("usa", "north america"),
            Map.entry("canada", "north america"), Map.entry("mexico", "north america"),
            Map.entry("brazil", "south america"), Map.entry("argentina", "south america"),
            Map.entry("australia", "oceania"), Map.entry("new zealand", "oceania"));

    private static final Map<String, String> CONTINENT_ALIASES = Map.of(
            "australia / oceania", "oceania",
            "australia", "oceania");

    /** Country for a stored state value (Indian states resolve to "india"). */
    public String countryOf(String state) {
        String value = normalize(state);
        if (value == null) {
            return null;
        }
        return INDIAN_STATES.contains(value) ? "india" : value;
    }

    /** Continent for a stored state value, or {@code null} when it cannot be resolved. */
    public String continentOf(String state) {
        String value = normalize(state);
        if (value == null) {
            return null;
        }
        String aliased = CONTINENT_ALIASES.getOrDefault(value, value);
        if (CONTINENT_ALIASES.containsValue(aliased) || "asia".equals(aliased) || "africa".equals(aliased)
                || "europe".equals(aliased) || "north america".equals(aliased)
                || "south america".equals(aliased) || "antarctica".equals(aliased)) {
            return aliased;
        }
        String country = countryOf(value);
        return COUNTRY_TO_CONTINENT.get(country);
    }

    public static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim().toLowerCase(Locale.ROOT);
        return trimmed.isEmpty() ? null : trimmed;
    }
}
