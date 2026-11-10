package com.endangered.species.repo;

import com.endangered.species.domain.Favourite;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FavouriteRepository extends JpaRepository<Favourite, Long> {

    Optional<Favourite> findByAnimalNameIgnoreCase(String animalName);

    boolean existsByAnimalNameIgnoreCase(String animalName);

    List<Favourite> findAllByOrderByAddedAtDesc();
}
