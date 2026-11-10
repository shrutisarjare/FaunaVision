package com.endangered.species.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.annotations.BatchSize;

/**
 * One row of a {@code {region}_{category}} sheet: a species as observed in a
 * particular state/country, together with its biennial population counts.
 */
@Entity
@Table(name = "species_record", indexes = {
        @Index(name = "idx_record_type", columnList = "animal_type"),
        @Index(name = "idx_record_name", columnList = "animal_name"),
        @Index(name = "idx_record_region", columnList = "region, category")
})
public class SpeciesRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Region region;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Category category;

    @Column(name = "animal_name", nullable = false, length = 200)
    private String animalName;

    @Column(name = "animal_type", length = 120)
    private String animalType;

    @Column(name = "scientific_name", length = 200)
    private String scientificName;

    @Column(length = 160)
    private String state;

    @Column(name = "habitat_location", length = 300)
    private String habitatLocation;

    @Column(length = 60)
    private String status;

    @OneToMany(mappedBy = "record", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("yearValue ASC")
    @BatchSize(size = 500)
    private List<PopulationEntry> populations = new ArrayList<>();

    protected SpeciesRecord() {
    }

    public SpeciesRecord(Region region, Category category, String animalName, String animalType,
                         String scientificName, String state, String habitatLocation, String status) {
        this.region = region;
        this.category = category;
        this.animalName = animalName;
        this.animalType = animalType;
        this.scientificName = scientificName;
        this.state = state;
        this.habitatLocation = habitatLocation;
        this.status = status;
    }

    public void addPopulation(int year, Integer population) {
        PopulationEntry entry = new PopulationEntry(this, year, population);
        populations.add(entry);
    }

    public Long getId() {
        return id;
    }

    public Region getRegion() {
        return region;
    }

    public Category getCategory() {
        return category;
    }

    public String getAnimalName() {
        return animalName;
    }

    public void setAnimalName(String animalName) {
        this.animalName = animalName;
    }

    public String getAnimalType() {
        return animalType;
    }

    public void setAnimalType(String animalType) {
        this.animalType = animalType;
    }

    public String getScientificName() {
        return scientificName;
    }

    public void setScientificName(String scientificName) {
        this.scientificName = scientificName;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getHabitatLocation() {
        return habitatLocation;
    }

    public void setHabitatLocation(String habitatLocation) {
        this.habitatLocation = habitatLocation;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public List<PopulationEntry> getPopulations() {
        return populations;
    }
}
