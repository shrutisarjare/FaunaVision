package com.endangered.species.web;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Health probe. The bare {@code /} now belongs to the React app, so the legacy greeting
 * moved here; it is also what the hosting platform polls.
 */
@RestController
public class RootController {

    @GetMapping("/api/health")
    public Map<String, String> health() {
        return Map.of("message", "API running");
    }
}
