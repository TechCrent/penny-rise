package com.stash.platform.user.service;

import com.stash.platform.notification.service.EmailSender;
import com.stash.platform.user.api.dto.LoginRequest;
import com.stash.platform.user.api.dto.LoginResponse;
import com.stash.platform.user.api.dto.RefreshRequest;
import com.stash.platform.user.api.dto.RefreshResponse;
import com.stash.platform.user.api.dto.SignupRequest;
import com.stash.platform.user.domain.RefreshToken;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("TokenRefreshService")
class TokenRefreshServiceTest {

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

    @Autowired TokenRefreshService tokenRefreshService;
    @Autowired LoginService loginService;
    @Autowired RefreshTokenService refreshTokenService;
    @Autowired SignupService signupService;
    @Autowired UserRepository userRepository;
    @Autowired EmailVerificationTokenRepository tokenRepository;
    @Autowired RefreshTokenRepository refreshTokenRepository;
    @Autowired LoginAttemptTracker attemptTracker;
    @MockBean  EmailSender emailSender;

    private static final String EMAIL    = "refresh-test@example.com";
    private static final String PASSWORD = "Str0ng!Pass";
    private static final String IP       = "127.0.0.1";

    private String initialRefreshToken;

    @BeforeEach
    void setUp() {
        refreshTokenRepository.deleteAll();
        tokenRepository.deleteAll();
        userRepository.deleteAll();
        attemptTracker.recordSuccess(EMAIL);

        signupService.signup(new SignupRequest(EMAIL, PASSWORD, "Refresh Test", null));

        var user = userRepository.findByEmail(EMAIL).orElseThrow();
        user.setEmailVerifiedAt(Instant.now());
        userRepository.save(user);

        LoginResponse login = loginService.login(
                new LoginRequest(EMAIL, PASSWORD, "d1", "Phone"), IP);
        initialRefreshToken = login.refreshToken();
    }

    private RefreshRequest req(String token) {
        return new RefreshRequest(token, "d1", "Phone");
    }

    // ── Happy path ────────────────────────────────────────────────────────

    @Test
    @DisplayName("happy refresh: returns new access_token, refresh_token, expires_in=900")
    void happy_refresh() {
        RefreshResponse response = tokenRefreshService.refresh(req(initialRefreshToken), IP);

        assertThat(response.accessToken()).isNotBlank();
        assertThat(response.refreshToken()).isNotBlank();
        assertThat(response.refreshToken()).isNotEqualTo(initialRefreshToken);
        assertThat(response.expiresIn()).isEqualTo(900L);
    }

    @Test
    @DisplayName("old token is revoked after successful refresh")
    void old_token_revoked_after_refresh() {
        tokenRefreshService.refresh(req(initialRefreshToken), IP);

        // Attempting to use the old token again must fail
        assertThatExceptionOfType(StashApiException.class)
                .isThrownBy(() -> tokenRefreshService.refresh(req(initialRefreshToken), IP))
                .satisfies(ex ->
                        assertThat(ex.getErrorCode().name())
                                .isEqualTo("AUTH_REFRESH_TOKEN_INVALID"));
    }

    @Test
    @DisplayName("new token from refresh can itself be refreshed (chain works)")
    void chained_refresh_works() {
        RefreshResponse r1 = tokenRefreshService.refresh(req(initialRefreshToken), IP);
        RefreshResponse r2 = tokenRefreshService.refresh(req(r1.refreshToken()), IP);

        assertThat(r2.accessToken()).isNotBlank();
        assertThat(r2.refreshToken()).isNotBlank();
    }

    @Test
    @DisplayName("response body contains no raw token values")
    void no_raw_token_in_response() {
        RefreshResponse response = tokenRefreshService.refresh(req(initialRefreshToken), IP);
        // The initial token should not appear anywhere in the response
        String responseStr = response.toString();
        assertThat(responseStr).doesNotContain(initialRefreshToken);
    }

    // ── Expired token ─────────────────────────────────────────────────────

    @Test
    @DisplayName("expired token returns 401 AUTH_REFRESH_TOKEN_EXPIRED")
    void expired_token_returns_401() {
        // Force expiry on the DB record
        List<RefreshToken> tokens = refreshTokenRepository.findAll();
        assertThat(tokens).isNotEmpty();
        RefreshToken token = tokens.get(0);
        token.setExpiresAt(Instant.now().minusSeconds(60));
        refreshTokenRepository.save(token);

        assertThatExceptionOfType(StashApiException.class)
                .isThrownBy(() -> tokenRefreshService.refresh(req(initialRefreshToken), IP))
                .satisfies(ex -> {
                    assertThat(ex.getHttpStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(ex.getErrorCode().name())
                            .isEqualTo("AUTH_REFRESH_TOKEN_EXPIRED");
                });
    }

    // ── Revoked token ─────────────────────────────────────────────────────

    @Test
    @DisplayName("token revoked by logout returns 401 AUTH_REFRESH_TOKEN_INVALID")
    void revoked_by_logout_returns_401() {
        refreshTokenService.revoke(initialRefreshToken);

        assertThatExceptionOfType(StashApiException.class)
                .isThrownBy(() -> tokenRefreshService.refresh(req(initialRefreshToken), IP))
                .satisfies(ex -> {
                    assertThat(ex.getHttpStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(ex.getErrorCode().name())
                            .isEqualTo("AUTH_REFRESH_TOKEN_INVALID");
                });
    }

    @Test
    @DisplayName("unknown / never-issued token returns 401 AUTH_REFRESH_TOKEN_INVALID")
    void unknown_token_returns_401() {
        assertThatExceptionOfType(StashApiException.class)
                .isThrownBy(() -> tokenRefreshService.refresh(
                        req("completely-unknown-token-value-xyz"), IP))
                .satisfies(ex -> {
                    assertThat(ex.getHttpStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(ex.getErrorCode().name())
                            .isEqualTo("AUTH_REFRESH_TOKEN_INVALID");
                });
    }

    // ── Replay detection ──────────────────────────────────────────────────

    @Test
    @DisplayName("replay: presenting a rotated token triggers chain revocation, returns 401")
    void replay_triggers_chain_revocation() {
        // Rotate the token once (T0 → T1)
        RefreshResponse r1 = tokenRefreshService.refresh(req(initialRefreshToken), IP);
        // Rotate again (T1 → T2)
        tokenRefreshService.refresh(req(r1.refreshToken()), IP);

        // Now replay T0 (already rotated)
        assertThatExceptionOfType(StashApiException.class)
                .isThrownBy(() -> tokenRefreshService.refresh(req(initialRefreshToken), IP))
                .satisfies(ex -> {
                    assertThat(ex.getHttpStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    // Same error code as any other revocation — no info leak
                    assertThat(ex.getErrorCode().name())
                            .isEqualTo("AUTH_REFRESH_TOKEN_INVALID");
                });

        // All 3 tokens should be revoked
        List<RefreshToken> all = refreshTokenRepository.findAll();
        assertThat(all).hasSize(3);
        assertThat(all).allMatch(RefreshToken::isRevoked);
    }

    @Test
    @DisplayName("replay and logout return the same error code — no info leak")
    void replay_and_logout_same_error_code() {
        // Logout path
        refreshTokenService.revoke(initialRefreshToken);
        StashApiException logoutException = catchThrowableOfType(
                () -> tokenRefreshService.refresh(req(initialRefreshToken), IP),
                StashApiException.class);

        // Replay path — need a fresh token
        LoginResponse freshLogin = loginService.login(
                new LoginRequest(EMAIL, PASSWORD, "d2", "Phone2"), IP);
        RefreshResponse rotated = tokenRefreshService.refresh(
                req(freshLogin.refreshToken()), IP);
        // Use the old (rotated) token to trigger replay
        StashApiException replayException = catchThrowableOfType(
                () -> tokenRefreshService.refresh(req(freshLogin.refreshToken()), IP),
                StashApiException.class);

        assertThat(logoutException.getErrorCode().name())
                .isEqualTo(replayException.getErrorCode().name())
                .isEqualTo("AUTH_REFRESH_TOKEN_INVALID");
    }

    // ── Performance ───────────────────────────────────────────────────────

    @Test
    @DisplayName("performance: refresh completes in under 100ms on warm connection pool")
    void performance_under_100ms() {
        // Warm up
        RefreshResponse r1 = tokenRefreshService.refresh(req(initialRefreshToken), IP);

        long start = System.currentTimeMillis();
        tokenRefreshService.refresh(req(r1.refreshToken()), IP);
        long elapsed = System.currentTimeMillis() - start;

        assertThat(elapsed)
                .as("Refresh should complete in under 100ms on warm pool, took %dms", elapsed)
                .isLessThan(100L);
    }
}