package com.endangered.species.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Species bookmarked by the visitor. */
@Entity
@Table(name = "favourite")
public class Favourite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "animal_name", nullable = false, unique = true, length = 200)
    private String animalName;

    @Column(name = "added_at", nullable = false)
    private Instant addedAt = Instant.now();

    protected Favourite() {
    }

    public Favourite(String animalName) {
        this.animalName = animalName;
    }

    public Long getId() {
        return id;
    }

    public String getAnimalName() {
        return animalName;
    }

    public Instant getAddedAt() {
        return addedAt;
    }
}
