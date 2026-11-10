package com.endangered.species.service;

import com.endangered.species.domain.Favourite;
import com.endangered.species.domain.HistoryEntry;
import com.endangered.species.repo.FavouriteRepository;
import com.endangered.species.repo.HistoryEntryRepository;
import com.endangered.species.web.Dtos;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Recently viewed species, bookmarks and the quiz generated from both. */
@Service
public class UserDataService {

    private static final int QUIZ_SIZE = 5;
    private static final List<String> STATUS_OPTIONS = List.of("Endangered", "Vulnerable", "Extinct", "Least Concern");

    private final HistoryEntryRepository history;
    private final FavouriteRepository favourites;

    public UserDataService(HistoryEntryRepository history, FavouriteRepository favourites) {
        this.history = history;
        this.favourites = favourites;
    }

    @Transactional(readOnly = true)
    public List<Dtos.HistoryItem> history() {
        return history.findAllByOrderByViewedAtDesc().stream().map(UserDataService::toItem).toList();
    }

    @Transactional
    public Map<String, Object> addHistory(String animal, String source) {
        String name = requireName(animal);
        String origin = source == null || source.isBlank() ? "detection" : source;
        HistoryEntry entry = history.findByAnimalNameIgnoreCase(name).orElseGet(() -> new HistoryEntry(name, origin));
        entry.touch(origin);
        history.save(entry);
        return Map.of("message", "Added to history", "data", toItem(entry));
    }

    @Transactional(readOnly = true)
    public List<Dtos.FavouriteItem> favourites() {
        return favourites.findAllByOrderByAddedAtDesc().stream()
                .map(f -> new Dtos.FavouriteItem(f.getAnimalName(), format(f.getAddedAt())))
                .toList();
    }

    @Transactional
    public Dtos.MessageResponse addFavourite(String animal) {
        String name = requireName(animal);
        if (!favourites.existsByAnimalNameIgnoreCase(name)) {
            favourites.save(new Favourite(name));
        }
        return new Dtos.MessageResponse("Added to favourites");
    }

    @Transactional
    public Dtos.MessageResponse removeFavourite(String animal) {
        favourites.findByAnimalNameIgnoreCase(requireName(animal)).ifPresent(favourites::delete);
        return new Dtos.MessageResponse("Removed from favourites");
    }

    @Transactional(readOnly = true)
    public List<Dtos.QuizQuestion> quiz() {
        Set<String> names = new LinkedHashSet<>();
        history.findAllByOrderByViewedAtDesc().forEach(entry -> names.add(entry.getAnimalName()));
        favourites.findAllByOrderByAddedAtDesc().forEach(entry -> names.add(entry.getAnimalName()));

        List<String> pool = new ArrayList<>(names);
        Collections.shuffle(pool);

        List<Dtos.QuizQuestion> questions = new ArrayList<>();
        for (String name : pool.subList(0, Math.min(QUIZ_SIZE, pool.size()))) {
            questions.add(new Dtos.QuizQuestion(
                    "What is the conservation status of " + name + "?",
                    STATUS_OPTIONS,
                    "Endangered"));
        }
        return questions;
    }

    private static Dtos.HistoryItem toItem(HistoryEntry entry) {
        return new Dtos.HistoryItem(entry.getAnimalName(), format(entry.getViewedAt()), entry.getSource());
    }

    private static String format(java.time.Instant instant) {
        return DateTimeFormatter.ISO_INSTANT.format(instant);
    }

    private static String requireName(String animal) {
        if (animal == null || animal.isBlank()) {
            throw new IllegalArgumentException("animal is required");
        }
        return animal.trim();
    }
}
