package com.stash.platform.user.service;

import com.stash.platform.notification.service.EmailSender;
import com.stash.platform.user.api.dto.LoginRequest;
import com.stash.platform.user.api.dto.SignupRequest;
import com.stash.platform.user.domain.PasswordResetToken;
import com.stash.platform.user.domain.RefreshToken;
import com.stash.platform.user.domain.User;
import com.stash.platform.user.repository.*;
import com.stash.shared.apierrors.StashApiException;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("ResetPasswordService")
class ResetPasswordServiceTest {

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16")
                    .withDatabaseName("monolith_test")
                    .withUsername("test")
                    .withPassword("test");

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",      postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired ResetPasswordService resetPasswordService;
    @Autowired SignupService signupService;
    @Autowired LoginService loginService;
    @Autowired PasswordHasher passwordHasher;
    @Autowired UserRepository userRepository;
    @Autowired PasswordResetTokenRepository resetTokenRepository;
    @Autowired EmailVerificationTokenRepository emailTokenRepository;
    @Autowired RefreshTokenRepository refreshTokenRepository;
    @Autowired LoginAttemptTracker attemptTracker;
    @MockBean  EmailSender emailSender;

    private static final String EMAIL        = "reset-test@example.com";
    private static final String OLD_PASSWORD = "OldStr0ng!Pass";
    private static final String NEW_PASSWORD = "NewStr0ng!Pass1";
    private static final String IP           = "127.0.0.1";

    private User user;
    private String validRawToken;

    @BeforeEach
    void setUp() {
        refreshTokenRepository.deleteAll();
        resetTokenRepository.deleteAll();
        emailTokenRepository.deleteAll();
        userRepository.deleteAll();
        attemptTracker.recordSuccess(EMAIL);

        signupService.signup(new SignupRequest(EMAIL, OLD_PASSWORD, "Reset Test", null));
        user = userRepository.findByEmail(EMAIL).orElseThrow();
        user.setEmailVerifiedAt(Instant.now());
        userRepository.save(user);

        validRawToken = issueTestResetToken(user);
    }

    private String issueTestResetToken(User u) {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        String raw  = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        String hash = sha256Hex(bytes);
        resetTokenRepository.save(new PasswordResetToken(u.getId(), hash));
        return raw;
    }

    // ── Happy path ────────────────────────────────────────────────────────

    @Test
    @DisplayName("happy reset: updates password_hash and password_changed_at")
    void happy_reset() {
        resetPasswordService.resetPassword(validRawToken, NEW_PASSWORD);

        User updated = userRepository.findByIdIncludingDeleted(user.getId()).orElseThrow();
        assertThat(passwordHasher.verify(NEW_PASSWORD, updated.getPasswordHash())).isTrue();
        assertThat(updated.getPasswordChangedAt()).isNotNull();
    }

    @Test
    @DisplayName("happy reset: marks the token consumed")
    void happy_reset_marks_token_consumed() {
        resetPasswordService.resetPassword(validRawToken, NEW_PASSWORD);

        String hash = sha256Hex(validRawToken.getBytes());
        PasswordResetToken token = resetTokenRepository.findByTokenHash(hash).orElseThrow();
        assertThat(token.isConsumed()).isTrue();
    }

    @Test
    @DisplayName("old password no longer works after reset")
    void old_password_no_longer_works() {
        resetPasswordService.resetPassword(validRawToken, NEW_PASSWORD);

        User updated = userRepository.findByIdIncludingDeleted(user.getId()).orElseThrow();
        assertThat(passwordHasher.verify(OLD_PASSWORD, updated.getPasswordHash())).isFalse();
    }

    @Test
    @DisplayName("login succeeds with new password after reset")
    void login_works_with_new_password_after_reset() {
        resetPasswordService.resetPassword(validRawToken, NEW_PASSWORD);

        assertThatNoException().isThrownBy(() ->
                loginService.login(new LoginRequest(EMAIL, NEW_PASSWORD, "d1", "Phone"), IP));
    }

    @Test
    @DisplayName("login fails with old password after reset")
    void login_fails_with_old_password_after_reset() {
        resetPasswordService.resetPassword(validRawToken, NEW_PASSWORD);

        assertThatExceptionOfType(StashApiException.class)
                .isThrownBy(() ->
                        loginService.login(new LoginRequest(EMAIL, OLD_PASSWORD, "d1", "Phone"), IP));
    }

    // ── All sessions revoked ─────────────────────────────────────────────

    @Test
    @DisplayName("all active refresh tokens are revoked after reset")
    void all_sessions_revoked_after_reset() {
        // Login twice (two sessions) BEFORE resetting password
        attemptTracker.recordSuccess(EMAIL);
        loginService.login(new LoginRequest(EMAIL, OLD_PASSWORD, "d1", "Phone A"), IP);
        loginService.login(new LoginRequest(EMAIL, OLD_PASSWORD, "d2", "Phone B"), IP);

        long activeBefore = refreshTokenRepository.findActiveByUserId(user.getId()).size();
        assertThat(activeBefore).isEqualTo(2);

        resetPasswordService.resetPassword(validRawToken, NEW_PASSWORD);

        List<RefreshToken> activeAfter = refreshTokenRepository.findActiveByUserId(user.getId());
        assertThat(activeAfter).isEmpty();
    }

    // ── Expired token ─────────────────────────────────────────────────────

    @Test
    @DisplayName("expired token returns 410 AUTH_RESET_TOKEN_EXPIRED")
    void expired_token_returns_410() {
        String hash = sha256Hex(validRawToken.getBytes());
        PasswordResetToken token = resetTokenRepository.findByTokenHash(hash).orElseThrow();
        token.setExpiresAt(Instant.now().minusSeconds(60));
        resetTokenRepository.save(token);

        assertThatExceptionOfType(StashApiException.class)
                .isThrownBy(() -> resetPasswordService.resetPassword(validRawToken, NEW_PASSWORD))
                .satisfies(ex -> {
                    assertThat(ex.getHttpStatus()).isEqualTo(HttpStatus.GONE);
                    assertThat(ex.getErrorCode().name()).isEqualTo("AUTH_RESET_TOKEN_EXPIRED");
                });
    }

    // ── Already consumed ──────────────────────────────────────────────────

    @Test
    @DisplayName("already-consumed token returns 409 AUTH_RESET_TOKEN_ALREADY_USED")
    void already_consumed_returns_409() {
        resetPasswordService.resetPassword(validRawToken, NEW_PASSWORD);

        assertThatExceptionOfType(StashApiException.class)
                .isThrownBy(() -> resetPasswordService.resetPassword(validRawToken, "AnotherP@ss1"))
                .satisfies(ex -> {
                    assertThat(ex.getHttpStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.getErrorCode().name()).isEqualTo("AUTH_RESET_TOKEN_ALREADY_USED");
                });
    }

    // ── Unknown token ─────────────────────────────────────────────────────

    @Test
    @DisplayName("unknown token returns 404 AUTH_RESET_TOKEN_INVALID")
    void unknown_token_returns_404() {
        assertThatExceptionOfType(StashApiException.class)
                .isThrownBy(() -> resetPasswordService.resetPassword(
                        "completely-unknown-token-value", NEW_PASSWORD))
                .satisfies(ex ->
                        assertThat(ex.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    // ── Weak password (validated at the DTO/Bean-Validation layer, not here) ─

    @Test
    @DisplayName("note: weak password rejection happens at @Valid layer on the controller, "
            + "not in the service — service trusts its caller")
    void weak_password_note() {
        // ResetPasswordService itself does not re-validate password strength;
        // that's the responsibility of @Valid on ResetPasswordRequest at the
        // controller boundary (consistent with SignupService's pattern).
        // This test documents that intentional boundary.
        assertThatNoException().isThrownBy(() ->
                resetPasswordService.resetPassword(validRawToken, "x")); // service-level: no strength check
    }

    // ── Helper ────────────────────────────────────────────────────────────

    private static String sha256Hex(byte[] input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input);
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}