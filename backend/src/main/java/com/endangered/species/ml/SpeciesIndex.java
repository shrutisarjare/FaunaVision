package com.endangered.species.ml;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.List;

/**
 * The catalogue projected into CLIP's text space: one unit-length vector per species.
 * Serialised to {@code data/species-embeddings.json} so the 700+ text encodes only run
 * once per catalogue change.
 */
public record SpeciesIndex(String fingerprint, List<Entry> species) {

    /** A catalogue species and the average embedding of its prompt variations. */
    public record Entry(String name, String scientificName, String animalType, float[] embedding) {
    }

    /** Never serialised: the cache file stores only the fingerprint and the species. */
    @JsonIgnore
    public boolean isEmpty() {
        return species == null || species.isEmpty();
    }
}
