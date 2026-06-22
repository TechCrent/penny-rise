package com.stash.platform.user.service;

import com.stash.platform.user.api.dto.LoginRequest;
import com.stash.platform.user.api.dto.LoginResponse;
import com.stash.platform.user.api.dto.SignupRequest;
import com.stash.platform.user.domain.AccountStatus;
import com.stash.platform.user.domain.User;
import com.stash.platform.user.repository.EmailVerificationTokenRepository;
import com.stash.platform.user.repository.RefreshTokenRepository;
import com.stash.platform.user.repository.UserRepository;
import com.stash.shared.apierrors.StashApiException;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;

import com.stash.platform.notification.service.EmailSender;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("LoginService")
class LoginServiceTest {

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

    @Autowired LoginService loginService;
    @Autowired SignupService signupService;
    @Autowired EmailVerificationService emailVerificationService;
    @Autowired UserRepository userRepository;
    @Autowired EmailVerificationTokenRepository tokenRepository;
    @Autowired RefreshTokenRepository refreshTokenRepository;
    @Autowired LoginAttemptTracker attemptTracker;
    @MockBean  EmailSender emailSender;

    private static final String TEST_EMAIL    = "login-test@example.com";
    private static final String TEST_PASSWORD = "Str0ng!Pass";
    private static final String IP            = "127.0.0.1";

    @BeforeEach
    void setUp() {
        refreshTokenRepository.deleteAll();
        tokenRepository.deleteAll();
        userRepository.deleteAll();

        // Register user
        signupService.signup(new SignupRequest(
                TEST_EMAIL, TEST_PASSWORD, "Login Test", null));

        // Verify email so login is permitted
        User user = userRepository.findByEmail(TEST_EMAIL).orElseThrow();
        user.setEmailVerifiedAt(Instant.now());
        userRepository.save(user);

        // Clear any leftover lockout state
        attemptTracker.recordSuccess(TEST_EMAIL);
    }

    private LoginRequest req(String email, String password) {
        return new LoginRequest(email, password, "device-1", "Test Phone");
    }

    // ── Happy path ────────────────────────────────────────────────────────

    @Test
    @DisplayName("happy login: returns access_token, refresh_token, expires_in, user profile")
    void happy_login() {
        LoginResponse response = loginService.login(req(TEST_EMAIL, TEST_PASSWORD), IP);

        assertThat(response.accessToken()).isNotBlank();
        assertThat(response.refreshToken()).isNotBlank();
        assertThat(response.expiresIn()).isEqualTo(900L); // 15 minutes
        assertThat(response.user().email()).isEqualTo(TEST_EMAIL);
        assertThat(response.user().kycStatus()).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("response body contains no password hash")
    void response_contains_no_password() {
        LoginResponse response = loginService.login(req(TEST_EMAIL, TEST_PASSWORD), IP);
        assertThat(response.toString())
                .doesNotContain(TEST_PASSWORD)
                .doesNotContain("$2a$");
    }

    @Test
    @DisplayName("failed-attempt counter resets on successful login")
    void counter_resets_on_success() {
        // Record 2 failures
        loginService_failWith(req(TEST_EMAIL, "WrongPass1!"));
        loginService_failWith(req(TEST_EMAIL, "WrongPass2!"));

        // Successful login resets
        loginService.login(req(TEST_EMAIL, TEST_PASSWORD), IP);

        // Now 4 more failures should be possible before lockout
        for (int i = 0; i < 4; i++) {
            loginService_failWith(req(TEST_EMAIL, "WrongPass!"));
        }
        // 5th failure triggers lockout — this means counter was indeed reset
        assertThatExceptionOfType(StashApiException.class)
                .isThrownBy(() -> loginService.login(req(TEST_EMAIL, "WrongPass!"), IP))
                .satisfies(ex ->
                        assertThat(ex.getErrorCode().name()).isEqualTo("AUTH_ACCOUNT_LOCKED"));
    }

    // ── Invalid credentials ───────────────────────────────────────────────

    @Test
    @DisplayName("wrong password returns 401 AUTH_INVALID_CREDENTIALS")
    void wrong_password_returns_401() {
        assertThatExceptionOfType(StashApiException.class)
                .isThrownBy(() -> loginService.login(req(TEST_EMAIL, "WrongP@ss1"), IP))
                .satisfies(ex -> {
                    assertThat(ex.getHttpStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(ex.getErrorCode().name()).isEqualTo("AUTH_INVALID_CREDENTIALS");
                });
    }

    @Test
    @DisplayName("unknown email returns same 401 as wrong password — no enumeration")
    void unknown_email_same_error_as_wrong_password() {
        assertThatExceptionOfType(StashApiException.class)
                .isThrownBy(() -> loginService.login(
                        req("nobody@example.com", "AnyPass1!"), IP))
                .satisfies(ex -> {
                    assertThat(ex.getHttpStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(ex.getErrorCode().name()).isEqualTo("AUTH_INVALID_CREDENTIALS");
                });
    }

    // ── Account state checks ──────────────────────────────────────────────

    @Test
    @DisplayName("unverified email returns 403 AUTH_EMAIL_NOT_VERIFIED")
    void unverified_email_returns_403() {
        // Clear email_verified_at
        User user = userRepository.findByEmail(TEST_EMAIL).orElseThrow();
        user.setEmailVerifiedAt(null);
        userRepository.save(user);

        assertThatExceptionOfType(StashApiException.class)
                .isThrownBy(() -> loginService.login(req(TEST_EMAIL, TEST_PASSWORD), IP))
                .satisfies(ex -> {
                    assertThat(ex.getHttpStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(ex.getErrorCode().name()).isEqualTo("AUTH_EMAIL_NOT_VERIFIED");
                });
    }

    @Test
    @DisplayName("suspended account returns 403 AUTH_ACCOUNT_SUSPENDED")
    void suspended_account_returns_403() {
        User user = userRepository.findByEmail(TEST_EMAIL).orElseThrow();
        user.setAccountStatus(AccountStatus.SUSPENDED);
        userRepository.save(user);

        assertThatExceptionOfType(StashApiException.class)
                .isThrownBy(() -> loginService.login(req(TEST_EMAIL, TEST_PASSWORD), IP))
                .satisfies(ex -> {
                    assertThat(ex.getHttpStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(ex.getErrorCode().name()).isEqualTo("AUTH_ACCOUNT_SUSPENDED");
                });
    }

    // ── Lockout ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("lockout triggers after 5 consecutive failures within 15 minutes")
    void lockout_after_5_failures() {
        for (int i = 0; i < 4; i++) {
            loginService_failWith(req(TEST_EMAIL, "BadPass" + i + "!"));
        }
        // 5th failure should trigger lockout
        assertThatExceptionOfType(StashApiException.class)
                .isThrownBy(() -> loginService.login(req(TEST_EMAIL, "BadPass5!"), IP))
                .satisfies(ex -> {
                    assertThat(ex.getHttpStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                    assertThat(ex.getErrorCode().name()).isEqualTo("AUTH_ACCOUNT_LOCKED");
                    assertThat(ex.getDetails()).containsKey("retry_after");
                    assertThat((Long) ex.getDetails().get("retry_after")).isPositive();
                });
    }

    @Test
    @DisplayName("correct password is blocked when account is locked out")
    void correct_password_blocked_during_lockout() {
        // Trigger lockout
        for (int i = 0; i < 5; i++) {
            loginService_failWith(req(TEST_EMAIL, "BadPass!"));
        }

        // Even correct password must be rejected during lockout
        assertThatExceptionOfType(StashApiException.class)
                .isThrownBy(() -> loginService.login(req(TEST_EMAIL, TEST_PASSWORD), IP))
                .satisfies(ex ->
                        assertThat(ex.getErrorCode().name()).isEqualTo("AUTH_ACCOUNT_LOCKED"));
    }

    // ── Timing side-channel ───────────────────────────────────────────────

    @Test
    @DisplayName("timing: wrong password on unknown email not significantly faster than on known email")
    void timing_mitigation() {
        int iterations = 5;

        long knownEmailMs = 0;
        for (int i = 0; i < iterations; i++) {
            long start = System.currentTimeMillis();
            loginService_failWith(req(TEST_EMAIL, "WrongP@ss1"));
            knownEmailMs += System.currentTimeMillis() - start;
        }

        // Reset counter
        attemptTracker.recordSuccess(TEST_EMAIL);

        long unknownEmailMs = 0;
        for (int i = 0; i < iterations; i++) {
            long start = System.currentTimeMillis();
            loginService_failWith(req("noone@example.com", "WrongP@ss1"));
            unknownEmailMs += System.currentTimeMillis() - start;
        }

        long avgKnown   = knownEmailMs   / iterations;
        long avgUnknown = unknownEmailMs / iterations;

        // The dummy hash approach means both paths run BCrypt.
        // We assert they're within 1 second of each other — a 1000ms gap
        // would be suspicious and indicate the timing mitigation is broken.
        assertThat(Math.abs(avgKnown - avgUnknown))
                .as("Timing difference between known and unknown email should be < 1000ms")
                .isLessThan(1000L);
    }

    // ── Helper ────────────────────────────────────────────────────────────

    private void loginService_failWith(LoginRequest request) {
        try {
            loginService.login(request, IP);
        } catch (StashApiException ignored) {
            // Expected — we're just driving the counter
        }
    }
}