package com.endangered.species.domain;

/** Habitat category used by the region sheets of the source workbook. */
public enum Category {
    TERRESTRIAL("terrestrial"),
    AQUATIC("aquatic"),
    AERIAL("aerial");

    private final String slug;

    Category(String slug) {
        this.slug = slug;
    }

    public String slug() {
        return slug;
    }

    public static Category fromSlug(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toLowerCase();
        for (Category category : values()) {
            if (category.slug.equals(normalized)) {
                return category;
            }
        }
        return null;
    }
}
