package com.stash.platform.user.service;

import com.stash.platform.user.domain.RefreshToken;
import com.stash.platform.user.repository.RefreshTokenRepository;
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
import java.util.UUID;

/**
 * Handles logout: revokes a single refresh token, or all of a user's
 * active tokens when {@code all_devices=true}.
 *
 * <p>Ownership check: the token presented must belong to the
 * authenticated caller (from the JWT). A caller cannot revoke
 * someone else's session even if they somehow obtain a raw token value.
 *
 * <p>Idempotent: revoking an already-revoked token is not an error.
 */
@Service
public class LogoutService {

    private static final Logger log = LoggerFactory.getLogger(LogoutService.class);

    private final RefreshTokenRepository refreshTokenRepository;
    private final RefreshTokenService refreshTokenService;

    public LogoutService(RefreshTokenRepository refreshTokenRepository,
                         RefreshTokenService refreshTokenService) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.refreshTokenService    = refreshTokenService;
    }

    /**
     * Logs out the authenticated user.
     *
     * @param rawRefreshToken     the raw token to revoke
     * @param allDevices          if true, revokes ALL active tokens for the user
     * @param authenticatedUserId the userId from the validated JWT (SecurityContext)
     * @throws StashApiException 403 if the token belongs to a different user
     */
    @Transactional
    public void logout(String rawRefreshToken, boolean allDevices, UUID authenticatedUserId) {
        // DO NOT log rawRefreshToken
        String hash = sha256Hex(rawRefreshToken);

        RefreshToken token = refreshTokenRepository.findByTokenHash(hash).orElse(null);

        if (token == null) {
            // Token doesn't exist — logout is idempotent, treat as already-logged-out.
            // No ownership check possible since there's nothing to compare against.
            log.debug("Logout: token not found — treating as already revoked");
            return;
        }

        // Ownership check — never let a caller revoke someone else's session
        if (!token.getUserId().equals(authenticatedUserId)) {
            log.warn("Logout rejected: token does not belong to authenticated user");
            throw new StashApiException(
                    ErrorCode.FORBIDDEN,
                    "You are not permitted to revoke this session.",
                    HttpStatus.FORBIDDEN
            );
        }

        if (allDevices) {
            refreshTokenService.revokeAllForUser(
                    authenticatedUserId, RefreshTokenService.REASON_USER_LOGOUT);
            log.info("Logout (all devices) userId={}", authenticatedUserId);
        } else {
            // Idempotent: revoke() already no-ops if already revoked
            refreshTokenService.revoke(rawRefreshToken);
            log.info("Logout (single device) userId={}", authenticatedUserId);
        }
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
