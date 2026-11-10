package com.endangered.species.web;

import com.endangered.species.service.UserDataService;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Recently viewed species, bookmarks and the generated quiz. These live under {@code /api}
 * because the React app owns the bare {@code /history}, {@code /favourites} and
 * {@code /quiz} pages, and a browser refresh must reach the app rather than the JSON.
 */
@RestController
public class UserDataController {

    private final UserDataService userData;

    public UserDataController(UserDataService userData) {
        this.userData = userData;
    }

    @GetMapping("/api/history")
    public List<Dtos.HistoryItem> history() {
        return userData.history();
    }

    @PostMapping("/api/history")
    public Map<String, Object> addHistory(@RequestParam String animal,
                                          @RequestParam(required = false, defaultValue = "detection") String source) {
        return userData.addHistory(animal, source);
    }

    @GetMapping("/api/favourites")
    public List<Dtos.FavouriteItem> favourites() {
        return userData.favourites();
    }

    @PostMapping("/api/favourites")
    public Dtos.MessageResponse addFavourite(@RequestParam String animal) {
        return userData.addFavourite(animal);
    }

    @DeleteMapping("/api/favourites")
    public Dtos.MessageResponse removeFavourite(@RequestParam String animal) {
        return userData.removeFavourite(animal);
    }

    @GetMapping("/api/quiz")
    public List<Dtos.QuizQuestion> quiz() {
        return userData.quiz();
    }
}
