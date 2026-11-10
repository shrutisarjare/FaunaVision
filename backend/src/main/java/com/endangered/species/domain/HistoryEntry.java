package com.endangered.species.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Most recently viewed species; newest first when sorted by {@code viewedAt} desc. */
@Entity
@Table(name = "history_entry")
public class HistoryEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "animal_name", nullable = false, unique = true, length = 200)
    private String animalName;

    @Column(length = 32)
    private String source = "detection";

    @Column(name = "viewed_at", nullable = false)
    private Instant viewedAt = Instant.now();

    protected HistoryEntry() {
    }

    public HistoryEntry(String animalName, String source) {
        this.animalName = animalName;
        this.source = source;
    }

    public Long getId() {
        return id;
    }

    public String getAnimalName() {
        return animalName;
    }

    public String getSource() {
        return source;
    }

    public Instant getViewedAt() {
        return viewedAt;
    }

    public void touch(String source) {
        this.source = source;
        this.viewedAt = Instant.now();
    }
}
