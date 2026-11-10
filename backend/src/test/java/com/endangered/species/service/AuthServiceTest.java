package com.endangered.species.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.endangered.species.domain.UserAccount;
import com.endangered.species.repo.UserAccountRepository;
import com.endangered.species.web.ApiException;
import com.endangered.species.web.Dtos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.http.HttpStatus;

/** Registration, login and profile rules against a real (in-memory) repository. */
@DataJpaTest
class AuthServiceTest {

    @Autowired
    private UserAccountRepository users;

    private AuthService auth;

    @BeforeEach
    void setUp() {
        auth = new AuthService(users, new JwtService("test-secret-that-is-long-enough-32b", 1));
    }

    @Test
    void registeringCreatesASessionAndHashesThePassword() {
        Dtos.LoginResponse session = auth.register(
                new Dtos.RegisterRequest("Asha", "Asha@Example.com", "secret1", "user"));

        assertThat(session.token()).isNotBlank();
        assertThat(session.user().email()).isEqualTo("asha@example.com");
        assertThat(session.user().id()).isNotBlank();

        UserAccount saved = users.findByEmailIgnoreCase("asha@example.com").orElseThrow();
        assertThat(saved.getPasswordHash()).isNotEqualTo("secret1").startsWith("$2");
        assertThat(saved.getRole()).isEqualTo("user");
    }

    @Test
    void theSameAddressCannotRegisterTwiceWhateverTheCase() {
        auth.register(new Dtos.RegisterRequest("Asha", "asha@example.com", "secret1", "user"));

        assertThatThrownBy(() -> auth.register(
                new Dtos.RegisterRequest("Someone", "ASHA@example.com", "secret2", "user")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void aShortPasswordIsRejected() {
        assertThatThrownBy(() -> auth.register(
                new Dtos.RegisterRequest("Asha", "asha@example.com", "12345", "user")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("at least 6");
    }

    @Test
    void onlyOneAdminAccountCanExist() {
        auth.register(new Dtos.RegisterRequest("Root", "root@example.com", "secret1", "admin"));

        assertThatThrownBy(() -> auth.register(
                new Dtos.RegisterRequest("Other", "other@example.com", "secret1", "admin")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Admin already exists");
    }

    @Test
    void loginAcceptsTheRightPasswordAndRejectsTheWrongOne() {
        auth.register(new Dtos.RegisterRequest("Asha", "asha@example.com", "secret1", "user"));

        assertThat(auth.login(new Dtos.LoginRequest("asha@example.com", "secret1", "user")).token())
                .isNotBlank();

        assertThatThrownBy(() -> auth.login(
                new Dtos.LoginRequest("asha@example.com", "secret2", "user")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Invalid credentials");
    }

    @Test
    void aPasswordIsNotTrimmedOnTheWayInOrOut() {
        auth.register(new Dtos.RegisterRequest("Asha", "asha@example.com", " secret ", "user"));

        assertThat(auth.login(new Dtos.LoginRequest("asha@example.com", " secret ", "user")).token())
                .isNotBlank();
        assertThatThrownBy(() -> auth.login(
                new Dtos.LoginRequest("asha@example.com", "secret", "user")))
                .hasMessageContaining("Invalid credentials");
    }

    @Test
    void aNormalAccountCannotLogInAsAdmin() {
        auth.register(new Dtos.RegisterRequest("Asha", "asha@example.com", "secret1", "user"));

        assertThatThrownBy(() -> auth.login(
                new Dtos.LoginRequest("asha@example.com", "secret1", "admin")))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> assertThat(((ApiException) error).getStatus())
                        .isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void aRoleOtherThanUserOrAdminIsRejected() {
        assertThatThrownBy(() -> auth.register(
                new Dtos.RegisterRequest("Asha", "asha@example.com", "secret1", "root")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Role must be");
    }

    @Test
    void theTokenIssuedAtRegistrationIdentifiesTheAccount() {
        Dtos.LoginResponse session = auth.register(
                new Dtos.RegisterRequest("Asha", "asha@example.com", "secret1", "user"));

        Dtos.UserView view = auth.session(session.token());

        assertThat(view.id()).isEqualTo(session.user().id());
        assertThat(view.email()).isEqualTo("asha@example.com");
        assertThat(view.role()).isEqualTo("user");
    }

    @Test
    void aSessionSurvivesAnEmailChange() {
        Dtos.LoginResponse session = auth.register(
                new Dtos.RegisterRequest("Asha", "asha@example.com", "secret1", "user"));

        // The token names the account by id, so an edit here must not sign the user out.
        auth.updateProfile(new Dtos.UpdateProfileRequest(
                session.user().id(), "Asha", "asha.rao@example.com"));

        assertThat(auth.session(session.token()).email()).isEqualTo("asha.rao@example.com");
    }

    @Test
    void aTamperedOrMissingTokenIsRejected() {
        Dtos.LoginResponse session = auth.register(
                new Dtos.RegisterRequest("Asha", "asha@example.com", "secret1", "user"));

        assertThatThrownBy(() -> auth.session(session.token() + "x"))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> assertThat(((ApiException) error).getStatus())
                        .isEqualTo(HttpStatus.UNAUTHORIZED));
        assertThatThrownBy(() -> auth.session("  "))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Please login");
    }

    @Test
    void anAdminAccountCannotLogInAsAUser() {
        auth.register(new Dtos.RegisterRequest("Root", "root@example.com", "secret1", "admin"));

        assertThatThrownBy(() -> auth.login(
                new Dtos.LoginRequest("root@example.com", "secret1", "user")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Select Admin")
                .satisfies(error -> assertThat(((ApiException) error).getStatus())
                        .isEqualTo(HttpStatus.FORBIDDEN));

        // The same account is accepted once the admin role is chosen.
        assertThat(auth.login(new Dtos.LoginRequest("root@example.com", "secret1", "admin")).token())
                .isNotBlank();
    }

    @Test
    void aWrongPasswordIsRefusedBeforeTheRoleIsLookedAt() {
        auth.register(new Dtos.RegisterRequest("Root", "root@example.com", "secret1", "admin"));

        // Otherwise the message would tell a stranger that the address is an admin account.
        assertThatThrownBy(() -> auth.login(
                new Dtos.LoginRequest("root@example.com", "wrong1", "user")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Invalid credentials");
    }

    @Test
    void aProfileUpdateCannotTakeAnotherAccountEmail() {
        auth.register(new Dtos.RegisterRequest("Asha", "asha@example.com", "secret1", "user"));
        auth.register(new Dtos.RegisterRequest("Ravi", "ravi@example.com", "secret1", "user"));
        UserAccount ravi = users.findByEmailIgnoreCase("ravi@example.com").orElseThrow();

        assertThatThrownBy(() -> auth.updateProfile(
                new Dtos.UpdateProfileRequest(String.valueOf(ravi.getId()), "Ravi", "ASHA@example.com")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("already registered");
    }

    @Test
    void aProfileUpdateMayKeepItsOwnEmailAndChangeTheName() {
        auth.register(new Dtos.RegisterRequest("Asha", "asha@example.com", "secret1", "user"));
        UserAccount asha = users.findByEmailIgnoreCase("asha@example.com").orElseThrow();

        auth.updateProfile(new Dtos.UpdateProfileRequest(
                String.valueOf(asha.getId()), "Asha Rao", "Asha@example.com"));

        UserAccount updated = users.findById(asha.getId()).orElseThrow();
        assertThat(updated.getName()).isEqualTo("Asha Rao");
        assertThat(updated.getEmail()).isEqualTo("asha@example.com");
    }
}
