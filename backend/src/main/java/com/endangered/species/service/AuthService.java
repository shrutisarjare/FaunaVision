package com.endangered.species.service;

import com.endangered.species.domain.UserAccount;
import com.endangered.species.repo.UserAccountRepository;
import com.endangered.species.web.ApiException;
import com.endangered.species.web.Dtos;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Registration, login and profile updates; replaces the Express auth route. */
@Service
public class AuthService {

    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    /** Shortest accepted password, mirrored by the register form. */
    private static final int MIN_PASSWORD_LENGTH = 6;

    /** The only roles the app knows; anything else is a client bug, not a new role. */
    private static final java.util.Set<String> ROLES = java.util.Set.of("user", "admin");

    private final UserAccountRepository users;
    private final JwtService jwt;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public AuthService(UserAccountRepository users, JwtService jwt) {
        this.users = users;
        this.jwt = jwt;
    }

    @Transactional
    public Dtos.LoginResponse register(Dtos.RegisterRequest request) {
        if (isBlank(request.name()) || isBlank(request.email()) || isBlank(request.password())) {
            throw ApiException.badRequest("All fields are required");
        }
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (!EMAIL.matcher(email).matches()) {
            throw ApiException.badRequest("Invalid email format");
        }
        // Passwords are never trimmed: a leading or trailing space is part of the secret.
        if (request.password().length() < MIN_PASSWORD_LENGTH) {
            throw ApiException.badRequest("Password must be at least " + MIN_PASSWORD_LENGTH
                    + " characters");
        }
        if (users.existsByEmailIgnoreCase(email)) {
            throw ApiException.badRequest("User already exists. Please login.");
        }

        String role = isBlank(request.role()) ? "user" : request.role().trim().toLowerCase(Locale.ROOT);
        if (!ROLES.contains(role)) {
            throw ApiException.badRequest("Role must be either user or admin");
        }
        if ("admin".equals(role) && users.existsByRole("admin")) {
            throw ApiException.badRequest("Admin already exists");
        }

        UserAccount user = users.save(new UserAccount(
                request.name().trim(), email, encoder.encode(request.password()), role));
        return new Dtos.LoginResponse(jwt.issue(user), toView(user));
    }

    @Transactional(readOnly = true)
    public Dtos.LoginResponse login(Dtos.LoginRequest request) {
        if (isBlank(request.email()) || isBlank(request.password())) {
            throw ApiException.badRequest("Email and password are required");
        }
        UserAccount user = users.findByEmailIgnoreCase(request.email().trim())
                .orElseThrow(() -> ApiException.badRequest("User not found. Please register first."));

        // The password is checked before the role, so a wrong role cannot reveal whether an
        // address belongs to an administrator.
        if (!encoder.matches(request.password(), user.getPasswordHash())) {
            throw ApiException.badRequest("Invalid credentials");
        }

        // The role chosen on the form has to match the account: an administrator may not slip
        // in through the user option, and a normal account may not claim the admin panel.
        boolean adminLogin = "admin".equalsIgnoreCase(request.role());
        boolean adminAccount = "admin".equalsIgnoreCase(user.getRole());
        if (adminLogin && !adminAccount) {
            throw ApiException.forbidden("This account is not an administrator");
        }
        if (!adminLogin && adminAccount) {
            throw ApiException.forbidden(
                    "This account is an administrator. Select Admin to login.");
        }
        return new Dtos.LoginResponse(jwt.issue(user), toView(user));
    }

    /**
     * Resolves the bearer token back to its account. The role is read from the database
     * rather than from the token, so a changed role takes effect on the next request.
     */
    @Transactional(readOnly = true)
    public Dtos.UserView session(String token) {
        if (isBlank(token)) {
            throw ApiException.unauthorized("Please login to continue");
        }
        long id;
        try {
            id = Long.parseLong(jwt.verify(token.trim()).getSubject());
        } catch (NumberFormatException e) {
            throw ApiException.unauthorized("Invalid session. Please login again.");
        }
        UserAccount user = users.findById(id)
                .orElseThrow(() -> ApiException.unauthorized("That account no longer exists"));
        return toView(user);
    }

    /** Legacy {@code POST /login} shape ({@code {"user": {"_id": ...}}}). */
    @Transactional(readOnly = true)
    public java.util.Map<String, Object> legacyLogin(String email, String password) {
        UserAccount user = users.findByEmailIgnoreCase(email == null ? "" : email.trim())
                .orElseThrow(() -> ApiException.notFound("User not found"));
        if (!encoder.matches(password, user.getPasswordHash())) {
            throw ApiException.badRequest("Invalid password");
        }
        return java.util.Map.of("user", java.util.Map.of(
                "_id", String.valueOf(user.getId()),
                "name", user.getName(),
                "email", user.getEmail(),
                "role", user.getRole()));
    }

    @Transactional
    public Dtos.MessageResponse updateProfile(Dtos.UpdateProfileRequest request) {
        if (isBlank(request.id())) {
            throw ApiException.badRequest("User ID missing");
        }
        long id;
        try {
            id = Long.parseLong(request.id());
        } catch (NumberFormatException e) {
            throw ApiException.badRequest("Invalid user ID");
        }
        UserAccount user = users.findById(id).orElseThrow(() -> ApiException.notFound("User not found"));
        if (!isBlank(request.name())) {
            user.setName(request.name().trim());
        }
        if (!isBlank(request.email())) {
            String email = request.email().trim().toLowerCase(Locale.ROOT);
            if (!EMAIL.matcher(email).matches()) {
                throw ApiException.badRequest("Invalid email format");
            }
            // The column is unique, so an unchecked change would surface as a 500.
            if (!email.equalsIgnoreCase(user.getEmail()) && users.existsByEmailIgnoreCase(email)) {
                throw ApiException.badRequest("That email is already registered");
            }
            user.setEmail(email);
        }
        users.save(user);
        return new Dtos.MessageResponse("Profile updated successfully");
    }

    private static Dtos.UserView toView(UserAccount user) {
        return new Dtos.UserView(String.valueOf(user.getId()), user.getName(), user.getEmail(), user.getRole());
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
