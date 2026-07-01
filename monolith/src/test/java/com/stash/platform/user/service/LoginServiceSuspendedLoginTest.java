package com.stash.platform.user.service;

import com.stash.platform.user.api.dto.LoginRequest;
import com.stash.platform.user.domain.AccountStatus;
import com.stash.platform.user.domain.User;
import com.stash.platform.user.repository.UserRepository;
import com.stash.shared.apierrors.StashApiException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the SUSPENDED account check in LoginService.login().
 *
 * <p>Uses Mockito mocks for all LoginService dependencies so no database,
 * container, or application context is required.
 */
@DisplayName("LoginService — suspended account check")
class LoginServiceSuspendedLoginTest {

    private static final String EMAIL    = "suspended@stash.app";
    private static final String PASSWORD = "Str0ng!Pass";
    private static final String IP       = "127.0.0.1";

    private final UserRepository       userRepository       = mock(UserRepository.class);
    private final PasswordHasher        passwordHasher       = mock(PasswordHasher.class);
    private final JwtTokenService       jwtTokenService      = mock(JwtTokenService.class);
    private final RefreshTokenService   refreshTokenService  = mock(RefreshTokenService.class);
    private final LoginAttemptTracker   attemptTracker       = mock(LoginAttemptTracker.class);
    private final BetaAllowlistService  betaAllowlistService = mock(BetaAllowlistService.class);

    private final LoginService loginService = new LoginService(
            userRepository, passwordHasher, jwtTokenService,
            refreshTokenService, attemptTracker, betaAllowlistService);

    @Test
    @DisplayName("login for a SUSPENDED account returns 403 AUTH_ACCOUNT_SUSPENDED after correct password")
    void suspendedAccountCannotLogin() {
        User user = new User(EMAIL, "hash", "Suspended User");
        user.setId(UUID.randomUUID());
        user.setEmailVerifiedAt(Instant.now());
        user.setAccountStatus(AccountStatus.SUSPENDED);

        when(attemptTracker.isLocked(EMAIL)).thenReturn(false);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordHasher.verify(PASSWORD, "hash")).thenReturn(true);

        assertThatThrownBy(() ->
                loginService.login(new LoginRequest(EMAIL, PASSWORD, null, null), IP))
                .isInstanceOf(StashApiException.class)
                .satisfies(ex -> {
                    var e = (StashApiException) ex;
                    assertThat(e.getHttpStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                });
    }

    @Test
    @DisplayName("suspended account check occurs AFTER password verification — wrong password still returns 401")
    void wrongPasswordSuspendedAccountReturns401() {
        User user = new User(EMAIL, "hash", "Suspended User");
        user.setId(UUID.randomUUID());
        user.setAccountStatus(AccountStatus.SUSPENDED);

        when(attemptTracker.isLocked(EMAIL)).thenReturn(false);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordHasher.verify(PASSWORD, "hash")).thenReturn(false);

        assertThatThrownBy(() ->
                loginService.login(new LoginRequest(EMAIL, PASSWORD, null, null), IP))
                .isInstanceOf(StashApiException.class)
                .satisfies(ex -> {
                    var e = (StashApiException) ex;
                    assertThat(e.getHttpStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
                });
    }
}
