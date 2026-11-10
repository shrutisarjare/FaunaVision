package com.endangered.species.repo;

import com.endangered.species.domain.Category;
import com.endangered.species.domain.Region;
import com.endangered.species.domain.SpeciesRecord;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SpeciesRecordRepository extends JpaRepository<SpeciesRecord, Long> {

    /** Mirrors the original global search: animal_type equals the term OR animal_name contains it. */
    @EntityGraph(attributePaths = "populations")
    @Query("""
            select r from SpeciesRecord r
            where lower(r.animalType) = lower(:term)
               or lower(r.animalName) like lower(concat('%', :term, '%'))
            order by r.id asc
            """)
    List<SpeciesRecord> search(@Param("term") String term);

    /** One row per distinct species, used to build the CLIP text index. */
    @Query("""
            select distinct r.animalName, r.scientificName, r.animalType
            from SpeciesRecord r
            order by r.animalName
            """)
    List<Object[]> distinctSpecies();

    @EntityGraph(attributePaths = "populations")
    List<SpeciesRecord> findByRegionAndCategoryAndStateIgnoreCaseAndAnimalTypeIgnoreCaseOrderByIdAsc(
            Region region, Category category, String state, String animalType);

    @EntityGraph(attributePaths = "populations")
    Optional<SpeciesRecord> findFirstByRegionAndCategoryAndStateIgnoreCaseAndAnimalNameContainingIgnoreCaseOrderByIdAsc(
            Region region, Category category, String state, String animalName);

    @EntityGraph(attributePaths = "populations")
    Optional<SpeciesRecord> findFirstByAnimalNameContainingIgnoreCaseOrderByIdAsc(String animalName);

    @EntityGraph(attributePaths = "populations")
    List<SpeciesRecord> findByAnimalNameContainingIgnoreCaseOrderByIdAsc(String animalName);

    /** Representative species of one catalogue type, used to answer audio identifications. */
    @EntityGraph(attributePaths = "populations")
    @Query("select r from SpeciesRecord r where lower(r.animalType) = lower(:type) order by r.id asc")
    List<SpeciesRecord> findByAnimalType(@Param("type") String type, Pageable pageable);

    @EntityGraph(attributePaths = "populations")
    List<SpeciesRecord> findByRegionAndCategoryOrderByIdAsc(Region region, Category category);

    @EntityGraph(attributePaths = "populations")
    List<SpeciesRecord> findAllByOrderByIdAsc();

    List<SpeciesRecord> findByRegionAndCategoryAndAnimalNameIgnoreCase(
            Region region, Category category, String animalName);

    List<SpeciesRecord> findByRegionAndCategoryAndAnimalNameContainingIgnoreCase(
            Region region, Category category, String animalName);
}
