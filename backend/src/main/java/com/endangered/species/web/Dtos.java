package com.endangered.species.web;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** JSON contracts shared with the React frontend (snake_case keys preserved). */
public final class Dtos {

    private Dtos() {
    }

    public record SpeciesSummary(
            String name,
            @JsonProperty("scientific_name") String scientificName,
            String location,
            String status,
            String image) {
    }

    public record PopulationPoint(int year, int population) {
    }

    public record Detection(
            String species,
            @JsonProperty("scientific_name") String scientificName,
            String state,
            String image,
            @JsonProperty("population_history") List<Integer> populationHistory,
            List<PopulationPoint> predictions,
            String trend,
            @JsonProperty("risk_level") String riskLevel) {
    }

    public record Analysis(
            String species,
            @JsonProperty("scientific_name") String scientificName,
            String state,
            String image,
            @JsonProperty("population_history") List<PopulationPoint> populationHistory,
            List<PopulationPoint> predictions,
            String trend,
            @JsonProperty("risk_level") String riskLevel) {
    }

    /** One candidate species for an upload, with the model's confidence in it. */
    public record Match(
            String name,
            @JsonProperty("scientific_name") String scientificName,
            double confidence) {
    }

    /**
     * One identification. {@code matches} ranks the candidates; {@code evidence} explains the
     * evidence behind them when the model could not name a single species (a sound only
     * reveals a group). Both are omitted from the legacy flows that have neither.
     */
    public record IdentifyResult(
            String species,
            String image,
            List<SpeciesSummary> results,
            List<Match> matches,
            String evidence) {
    }

    public record TextInput(String text) {
    }

    public record QuizQuestion(String question, List<String> options, String answer) {
    }

    public record HistoryItem(String name, String timestamp, String source) {
    }

    public record FavouriteItem(String name, @JsonProperty("added_at") String addedAt) {
    }

    public record UserView(
            @JsonProperty("_id") String id,
            String name,
            String email,
            String role) {
    }

    public record LoginResponse(String token, UserView user) {
    }

    public record LoginRequest(String email, String password, String role) {
    }

    public record RegisterRequest(String name, String email, String password, String role) {
    }

    public record UpdateProfileRequest(@JsonProperty("_id") String id, String name, String email) {
    }

    public record ContactRequest(String name, String email, String message) {
    }

    public record MessageResponse(String message) {
    }

    public record ErrorResponse(String error) {
    }
}
