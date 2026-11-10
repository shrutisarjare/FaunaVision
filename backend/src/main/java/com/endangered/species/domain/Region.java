package com.endangered.species.domain;

/** Geographic level used by the region sheets of the source workbook. */
public enum Region {
    INDIA("india"),
    PANASIA("panasia"),
    GLOBAL("global");

    private final String slug;

    Region(String slug) {
        this.slug = slug;
    }

    public String slug() {
        return slug;
    }

    public static Region fromSlug(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toLowerCase();
        for (Region region : values()) {
            if (region.slug.equals(normalized)) {
                return region;
            }
        }
        return null;
    }
}
