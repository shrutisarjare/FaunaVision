package com.endangered.species.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/** A single biennial population observation belonging to a {@link SpeciesRecord}. */
@Entity
@Table(name = "population_entry", uniqueConstraints = {
        @UniqueConstraint(name = "uk_population_record_year", columnNames = {"record_id", "year_value"})
})
public class PopulationEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "record_id", nullable = false)
    private SpeciesRecord record;

    @Column(name = "year_value", nullable = false)
    private int yearValue;

    private Integer population;

    protected PopulationEntry() {
    }

    PopulationEntry(SpeciesRecord record, int yearValue, Integer population) {
        this.record = record;
        this.yearValue = yearValue;
        this.population = population;
    }

    public void setPopulation(Integer population) {
        this.population = population;
    }

    public Long getId() {
        return id;
    }

    public int getYearValue() {
        return yearValue;
    }

    public Integer getPopulation() {
        return population;
    }
}
