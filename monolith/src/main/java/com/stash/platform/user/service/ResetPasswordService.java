package com.stash.platform.user.service;

import com.stash.platform.user.domain.PasswordResetToken;
import com.stash.platform.user.domain.User;
import com.stash.platform.user.repository.PasswordResetTokenRepository;
import com.stash.platform.user.repository.UserRepository;
import com.stash.shared.apierrors.ErrorCode;
import com.stash.shared.apierrors.StashApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;

/**
 * Completes the password reset flow: validates the token, sets a new
 * password, and revokes all active sessions as a defence-in-depth measure.
 *
 * <p>Transaction boundary: token consumption, password update, and
 * refresh-token revocation all happen atomically. If any step fails,
 * none of them are applied.
 *
 * <p><strong>PII invariants:</strong>
 * <ul>
 *   <li>Raw token never logged.</li>
 *   <li>New password never logged.</li>
 * </ul>
 */
@Service
public class ResetPasswordService {

    private static final Logger log = LoggerFactory.getLogger(ResetPasswordService.class);

    private final PasswordResetTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;
    private final RefreshTokenService refreshTokenService;

    public ResetPasswordService(PasswordResetTokenRepository tokenRepository,
                                UserRepository userRepository,
                                PasswordHasher passwordHasher,
                                RefreshTokenService refreshTokenService) {
        this.tokenRepository      = tokenRepository;
        this.userRepository       = userRepository;
        this.passwordHasher       = passwordHasher;
        this.refreshTokenService  = refreshTokenService;
    }

    /**
     * Resets a user's password using a valid reset token.
     *
     * @param rawToken    the plaintext token from the reset URL
     * @param newPassword the new plaintext password (already validated for strength)
     * @throws StashApiException 404 unknown token, 409 already used, 410 expired
     */
    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        // DO NOT log rawToken or newPassword
        String hash = sha256Hex(rawToken);

        PasswordResetToken token = tokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new StashApiException(
                        ErrorCode.AUTH_RESET_TOKEN_INVALID,
                        "Password reset token is invalid.",
                        HttpStatus.NOT_FOUND
                ));

        if (token.isConsumed()) {
            log.debug("Reset rejected: token already consumed userId={}", token.getUserId());
            throw new StashApiException(
                    ErrorCode.AUTH_RESET_TOKEN_ALREADY_USED,
                    "This password reset link has already been used. " +
                            "Please request a new one.",
                    HttpStatus.CONFLICT
            );
        }

        if (token.isExpired()) {
            log.debug("Reset rejected: token expired userId={}", token.getUserId());
            throw new StashApiException(
                    ErrorCode.AUTH_RESET_TOKEN_EXPIRED,
                    "This password reset link has expired. " +
                            "Please request a new one.",
                    HttpStatus.GONE
            );
        }

        User user = userRepository.findByIdIncludingDeleted(token.getUserId())
                .orElseThrow(() -> new StashApiException(
                        ErrorCode.NOT_FOUND,
                        "User not found.",
                        HttpStatus.NOT_FOUND
                ));

        // Hash and apply the new password — DO NOT log newPassword
        String newHash = passwordHasher.hash(newPassword);
        user.setPasswordHash(newHash);
        user.setPasswordChangedAt(Instant.now());
        userRepository.save(user);

        // Consume the token
        token.setConsumedAt(Instant.now());
        tokenRepository.save(token);

        // Defence-in-depth: revoke ALL active sessions.
        // If an attacker triggered this reset, their session is killed too.
        refreshTokenService.revokeAllForUser(
                user.getId(), RefreshTokenService.REASON_ADMIN_FORCE);
        // Note: REASON_ADMIN_FORCE is reused here as the closest existing
        // canonical reason for "all sessions force-revoked by a security event"
        // rather than introducing a new DB-level enum value for this issue.

        log.info("Password reset completed userId={}", user.getId());
        // Note: email address NOT logged.
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}