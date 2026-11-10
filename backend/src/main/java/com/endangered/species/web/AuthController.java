package com.endangered.species.web;

import com.endangered.species.service.AuthService;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * Authentication routes. {@code /api/auth/*} keeps the Express contract used by the
 * React app, while {@code /login} and {@code /update-profile} keep the older FastAPI
 * contract for compatibility.
 */
@RestController
public class AuthController {

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/api/auth/register")
    public ResponseEntity<Map<String, Object>> register(@RequestBody Dtos.RegisterRequest request) {
        if (request == null) {
            throw ApiException.badRequest("All fields are required");
        }
        // Registering signs the new account in: the client would otherwise have to post the
        // same credentials again immediately afterwards.
        Dtos.LoginResponse response = auth.register(request);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", "Registration successful");
        body.put("token", response.token());
        body.put("user", response.user());
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @PostMapping("/api/auth/login")
    public Map<String, Object> login(@RequestBody Dtos.LoginRequest request) {
        Dtos.LoginResponse response = auth.login(request == null
                ? new Dtos.LoginRequest(null, null, null)
                : request);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", "Login successful");
        body.put("token", response.token());
        body.put("user", response.user());
        return body;
    }

    /**
     * Confirms that a stored session is still valid and returns the current account, so the
     * app can drop a stale token instead of failing later on a write.
     */
    @GetMapping("/api/auth/me")
    public Dtos.UserView me(@RequestHeader(value = "Authorization", required = false) String header) {
        return auth.session(bearer(header));
    }

    @PostMapping("/login")
    public Map<String, Object> legacyLogin(@RequestBody Map<String, String> body) {
        String email = body == null ? null : body.get("email");
        String password = body == null ? null : body.get("password");
        return auth.legacyLogin(email, password);
    }

    /** Accepts both {@code Bearer <token>} and a bare token. */
    private static String bearer(String header) {
        if (header == null) {
            return null;
        }
        String value = header.trim();
        return value.regionMatches(true, 0, "Bearer ", 0, 7) ? value.substring(7).trim() : value;
    }

    @PutMapping("/update-profile")
    public Dtos.MessageResponse updateProfile(@RequestBody Dtos.UpdateProfileRequest request) {
        return auth.updateProfile(request == null
                ? new Dtos.UpdateProfileRequest(null, null, null)
                : request);
    }
}
