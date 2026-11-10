package com.endangered.species.repo;

import com.endangered.species.domain.HistoryEntry;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HistoryEntryRepository extends JpaRepository<HistoryEntry, Long> {

    Optional<HistoryEntry> findByAnimalNameIgnoreCase(String animalName);

    List<HistoryEntry> findAllByOrderByViewedAtDesc();

    List<HistoryEntry> findTop20ByOrderByViewedAtDesc();
}
