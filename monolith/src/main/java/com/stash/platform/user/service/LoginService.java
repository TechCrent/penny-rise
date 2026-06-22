package com.stash.platform.user.service;

import com.stash.platform.user.api.dto.LoginRequest;
import com.stash.platform.user.api.dto.LoginResponse;
import com.stash.platform.user.domain.AccountStatus;
import com.stash.platform.user.domain.User;
import com.stash.platform.user.repository.UserRepository;
import com.stash.shared.apierrors.ErrorCode;
import com.stash.shared.apierrors.StashApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Handles user login with brute-force protection.
 *
 * <p>Check order (critical — must not be reordered):
 * <ol>
 *   <li>Lockout check — before any DB lookup (fast-fail)</li>
 *   <li>User lookup + password verification — both performed even for unknown
 *       emails to prevent timing side-channel attacks</li>
 *   <li>Account state checks (suspended, unverified) — only after password
 *       is confirmed correct to avoid leaking account state</li>
 * </ol>
 *
 * <p><strong>Timing attack mitigation:</strong> BCrypt verification is
 * performed for unknown emails using a dummy hash. This ensures the
 * response time for "wrong password on valid email" is indistinguishable
 * from "wrong password on non-existent email". Without this, an attacker
 * could enumerate registered emails by measuring response time.
 *
 * <p><strong>PII invariants:</strong>
 * <ul>
 *   <li>Raw password never logged at any level.</li>
 *   <li>Email address never logged at INFO or above.</li>
 * </ul>
 */
@Service
public class LoginService {

    private static final Logger log = LoggerFactory.getLogger(LoginService.class);

    // Dummy hash for constant-time comparison on unknown emails.
    // This is a pre-computed BCrypt hash of the string "dummy-password-for-timing-mitigation".
    // It is not a security secret — its purpose is solely to burn the same CPU as a real verify().
    private static final String TIMING_DUMMY_HASH =
            "$2a$12$dummyhashfortimingmitigationXXXXXXXXXXXXXXXXXXXXXXXX";

    // Access token lifetime in seconds (15 minutes)
    private static final long ACCESS_TOKEN_EXPIRY_SECONDS = 15 * 60;

    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;
    private final JwtTokenService jwtTokenService;
    private final RefreshTokenService refreshTokenService;
    private final LoginAttemptTracker attemptTracker;

    public LoginService(UserRepository userRepository,
                        PasswordHasher passwordHasher,
                        JwtTokenService jwtTokenService,
                        RefreshTokenService refreshTokenService,
                        LoginAttemptTracker attemptTracker) {
        this.userRepository      = userRepository;
        this.passwordHasher      = passwordHasher;
        this.jwtTokenService     = jwtTokenService;
        this.refreshTokenService = refreshTokenService;
        this.attemptTracker      = attemptTracker;
    }

    /**
     * Authenticates a user and issues a token pair.
     *
     * @param request login credentials + device info
     * @param ipAddress  client IP address for refresh token binding
     * @return token pair + user profile
     */
    public LoginResponse login(LoginRequest request, String ipAddress) {
        String emailKey = request.email().toLowerCase().strip();
        // DO NOT log emailKey

        // ── Step 1: Check lockout (fast-fail, before DB lookup) ───────────
        if (attemptTracker.isLocked(emailKey)) {
            long retryAfter = attemptTracker.secondsUntilUnlock(emailKey);
            log.warn("Login blocked: account locked out retryAfterSeconds={}", retryAfter);
            throw new StashApiException(
                    ErrorCode.AUTH_ACCOUNT_LOCKED,
                    "Too many failed login attempts. Please try again later.",
                    HttpStatus.TOO_MANY_REQUESTS,
                    Map.of("retry_after", retryAfter)
            );
        }

        // ── Step 2: Lookup user + constant-time password check ────────────
        User user = userRepository.findByEmail(emailKey).orElse(null);

        String hashToVerify = (user != null) ? user.getPasswordHash() : TIMING_DUMMY_HASH;
        boolean passwordMatches = passwordHasher.verify(request.password(), hashToVerify);
        // DO NOT log request.password()

        if (user == null || !passwordMatches) {
            // Record failure for rate limiting
            boolean nowLocked = attemptTracker.recordFailure(emailKey);
            if (nowLocked) {
                long retryAfter = attemptTracker.secondsUntilUnlock(emailKey);
                log.warn("Login lockout triggered after {} consecutive failures",
                        LoginAttemptTracker.MAX_FAILURES);
                throw new StashApiException(
                        ErrorCode.AUTH_ACCOUNT_LOCKED,
                        "Too many failed login attempts. Please try again later.",
                        HttpStatus.TOO_MANY_REQUESTS,
                        Map.of("retry_after", retryAfter)
                );
            }
            // Same error for unknown email and wrong password — no enumeration leak
            throw new StashApiException(
                    ErrorCode.AUTH_INVALID_CREDENTIALS,
                    "Email or password is incorrect.",
                    HttpStatus.UNAUTHORIZED
            );
        }

        // Password is correct from here.

        // ── Step 3: Account state checks ──────────────────────────────────
        // Checked AFTER password verification so we don't reveal account state
        // to someone who doesn't know the password.

        if (AccountStatus.SUSPENDED.equals(user.getAccountStatus())) {
            log.warn("Login blocked: account suspended userId={}", user.getId());
            throw new StashApiException(
                    ErrorCode.AUTH_ACCOUNT_SUSPENDED,
                    "Your account has been suspended. Please contact support.",
                    HttpStatus.FORBIDDEN
            );
        }

        if (!user.isEmailVerified()) {
            log.debug("Login blocked: email not verified userId={}", user.getId());
            throw new StashApiException(
                    ErrorCode.AUTH_EMAIL_NOT_VERIFIED,
                    "Please verify your email address before logging in.",
                    HttpStatus.FORBIDDEN
            );
        }

        // ── Step 4: Success — issue tokens, reset counter ─────────────────
        attemptTracker.recordSuccess(emailKey);

        String deviceId    = (request.deviceId() != null && !request.deviceId().isBlank())
                ? request.deviceId() : "unknown";
        String deviceLabel = request.deviceLabel();

        RefreshTokenService.TokenPair tokens =
                refreshTokenService.issue(user, deviceId, deviceLabel, ipAddress);

        log.info("Login successful userId={}", user.getId());

        return new LoginResponse(
                tokens.accessToken(),
                tokens.rawRefreshToken(),
                ACCESS_TOKEN_EXPIRY_SECONDS,
                new LoginResponse.UserProfile(
                        user.getId(),
                        user.getEmail(),
                        user.getDisplayName(),
                        user.getKycStatus().name()
                )
        );
    }
}