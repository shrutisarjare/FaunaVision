package com.endangered.species.ml;

import java.util.Locale;

/**
 * Collapses verbose ImageNet labels ("Bengal tiger, Panthera tigris tigris") onto the
 * coarse species names stored in the catalogue, mirroring the legacy
 * {@code clean_species} helper.
 */
public final class LabelCleaner {

    private LabelCleaner() {
    }

    public static String clean(String label) {
        String value = label == null ? "" : label.toLowerCase(Locale.ROOT).trim();
        if (value.contains("tiger")) {
            return "tiger";
        }
        if (value.contains("lion")) {
            return "lion";
        }
        if (value.contains("elephant")) {
            return "elephant";
        }
        if (value.contains("bear")) {
            return "bear";
        }
        if (value.contains("wolf")) {
            return "wolf";
        }
        return value;
    }
}
