package com.stash.platform.user.service;

import com.stash.platform.notification.service.EmailSender;
import com.stash.platform.user.api.dto.LoginRequest;
import com.stash.platform.user.api.dto.LoginResponse;
import com.stash.platform.user.api.dto.SignupRequest;
import com.stash.platform.user.domain.RefreshToken;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("LogoutService")
class LogoutServiceTest {

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

    @Autowired LogoutService logoutService;
    @Autowired LoginService loginService;
    @Autowired SignupService signupService;
    @Autowired UserRepository userRepository;
    @Autowired EmailVerificationTokenRepository tokenRepository;
    @Autowired RefreshTokenRepository refreshTokenRepository;
    @Autowired LoginAttemptTracker attemptTracker;
    @MockBean  EmailSender emailSender;

    private static final String EMAIL_A = "logout-a@example.com";
    private static final String EMAIL_B = "logout-b@example.com";
    private static final String PASSWORD = "Str0ng!Pass";
    private static final String IP = "127.0.0.1";

    private User userA;
    private User userB;
    private String tokenA;

    @BeforeEach
    void setUp() {
        refreshTokenRepository.deleteAll();
        tokenRepository.deleteAll();
        userRepository.deleteAll();
        attemptTracker.recordSuccess(EMAIL_A);
        attemptTracker.recordSuccess(EMAIL_B);

        signupService.signup(new SignupRequest(EMAIL_A, PASSWORD, "User A", null));
        signupService.signup(new SignupRequest(EMAIL_B, PASSWORD, "User B", null));

        userA = userRepository.findByEmail(EMAIL_A).orElseThrow();
        userA.setEmailVerifiedAt(Instant.now());
        userRepository.save(userA);

        userB = userRepository.findByEmail(EMAIL_B).orElseThrow();
        userB.setEmailVerifiedAt(Instant.now());
        userRepository.save(userB);

        LoginResponse loginA = loginService.login(
                new LoginRequest(EMAIL_A, PASSWORD, "d1", "Phone A"), IP);
        tokenA = loginA.refreshToken();
    }

    // ΓöÇΓöÇ Happy path ΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇ

    @Test
    @DisplayName("happy logout: revokes the supplied token")
    void happy_logout() {
        logoutService.logout(tokenA, false, userA.getId());

        List<RefreshToken> tokens = refreshTokenRepository.findAll();
        RefreshToken revoked = tokens.stream()
                .filter(RefreshToken::isRevoked)
                .findFirst()
                .orElseThrow();

        assertThat(revoked.getRevokedReason())
                .isEqualTo(RefreshTokenService.REASON_USER_LOGOUT);
    }

    @Test
    @DisplayName("revoked token cannot be used for a subsequent refresh")
    void revoked_token_cannot_refresh() {
        logoutService.logout(tokenA, false, userA.getId());

        var refreshService = refreshTokenServiceFromContext();
        assertThatThrownBy(() ->
                refreshService.refresh(tokenA, "d1", "Phone A", IP))
                .isInstanceOf(com.stash.platform.user.exception.RefreshTokenException.class);
    }

    // ΓöÇΓöÇ all_devices ΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇ

    @Test
    @DisplayName("all_devices=true revokes every active token for the user")
    void all_devices_revokes_everything() {
        // Issue a second session for user A
        LoginResponse secondLogin = loginService.login(
                new LoginRequest(EMAIL_A, PASSWORD, "d2", "Phone A2"), IP);

        logoutService.logout(tokenA, true, userA.getId());

        List<RefreshToken> userATokens = refreshTokenRepository.findAll().stream()
                .filter(t -> t.getUserId().equals(userA.getId()))
                .toList();

        assertThat(userATokens).hasSize(2);
        assertThat(userATokens).allMatch(RefreshToken::isRevoked);
    }

    @Test
    @DisplayName("all_devices=true does not affect another user's tokens")
    void all_devices_does_not_affect_other_users() {
        LoginResponse loginB = loginService.login(
                new LoginRequest(EMAIL_B, PASSWORD, "d3", "Phone B"), IP);

        logoutService.logout(tokenA, true, userA.getId());

        List<RefreshToken> userBTokens = refreshTokenRepository.findAll().stream()
                .filter(t -> t.getUserId().equals(userB.getId()))
                .toList();

        assertThat(userBTokens).hasSize(1);
        assertThat(userBTokens.get(0).isRevoked()).isFalse();
    }

    // ΓöÇΓöÇ Ownership check ΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇ

    @Test
    @DisplayName("logging out with another user's token returns 403")
    void other_users_token_returns_403() {
        assertThatExceptionOfType(StashApiException.class)
                .isThrownBy(() -> logoutService.logout(tokenA, false, userB.getId()))
                .satisfies(ex ->
                        assertThat(ex.getHttpStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    @DisplayName("token is NOT revoked when ownership check fails")
    void token_not_revoked_on_ownership_failure() {
        try {
            logoutService.logout(tokenA, false, userB.getId());
        } catch (StashApiException ignored) {}

        List<RefreshToken> tokens = refreshTokenRepository.findAll();
        assertThat(tokens).noneMatch(RefreshToken::isRevoked);
    }

    // ΓöÇΓöÇ Idempotency ΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇ

    @Test
    @DisplayName("logging out an already-revoked token does not throw ΓÇö idempotent")
    void already_revoked_token_idempotent() {
        logoutService.logout(tokenA, false, userA.getId());

        assertThatNoException()
                .isThrownBy(() -> logoutService.logout(tokenA, false, userA.getId()));
    }

    @Test
    @DisplayName("logging out an unknown token does not throw ΓÇö idempotent")
    void unknown_token_idempotent() {
        assertThatNoException()
                .isThrownBy(() -> logoutService.logout(
                        "never-issued-token-value", false, userA.getId()));
    }

    // ΓöÇΓöÇ Helper: access the RefreshTokenService bean ΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇ

    @Autowired
    private RefreshTokenService refreshTokenServiceField;

    private RefreshTokenService refreshTokenServiceFromContext() {
        return refreshTokenServiceField;
    }
}
