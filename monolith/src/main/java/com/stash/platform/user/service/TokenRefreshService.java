package com.stash.platform.user.service;

import com.stash.platform.user.api.dto.RefreshRequest;
import com.stash.platform.user.api.dto.RefreshResponse;
import com.stash.platform.user.exception.RefreshTokenException;
import com.stash.shared.apierrors.ErrorCode;
import com.stash.shared.apierrors.StashApiException;
import io.jsonwebtoken.ExpiredJwtException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * HTTP adapter for the refresh-token exchange flow.
 *
 * <p>Delegates to {@link RefreshTokenService} and maps exceptions to the
 * correct HTTP error codes for this endpoint.
 *
 * <p><strong>Information disclosure rules:</strong>
 * <ul>
 *   <li>Expired tokens → 401 {@code AUTH_REFRESH_TOKEN_EXPIRED} (UX: show "session expired")</li>
 *   <li>Revoked tokens (logout, rotation, replay) → 401 {@code AUTH_REFRESH_TOKEN_INVALID}</li>
 *   <li>No distinction is made between "token was logged out" and "token was part of a replay" —
 *       revealing this distinction would give an attacker information about whether their
 *       replay was detected.</li>
 * </ul>
 *
 * <p><strong>PII invariants:</strong>
 * <ul>
 *   <li>Raw token value is NEVER logged.</li>
 *   <li>New raw token value is NEVER logged.</li>
 * </ul>
 */
@Service
public class TokenRefreshService {

    private static final Logger log = LoggerFactory.getLogger(TokenRefreshService.class);

    private static final long ACCESS_TOKEN_EXPIRY_SECONDS = 15 * 60; // 900

    private final RefreshTokenService refreshTokenService;

    public TokenRefreshService(RefreshTokenService refreshTokenService) {
        this.refreshTokenService = refreshTokenService;
    }

    /**
     * Exchanges a valid refresh token for a new token pair.
     *
     * @param request   contains the raw refresh token + optional device info
     * @param ipAddress client IP for the new token record
     * @return new access token + refresh token
     * @throws StashApiException 401 on any invalid/expired/revoked token
     */
    public RefreshResponse refresh(RefreshRequest request, String ipAddress) {
        // DO NOT log request.refreshToken()
        String deviceId    = request.deviceId()    != null ? request.deviceId()    : "unknown";
        String deviceLabel = request.deviceLabel()  != null ? request.deviceLabel() : null;

        try {
            RefreshTokenService.TokenPair pair = refreshTokenService.refresh(
                    request.refreshToken(), deviceId, deviceLabel, ipAddress);

            log.info("Token refresh successful");
            // DO NOT log pair.rawRefreshToken() or pair.accessToken()

            return new RefreshResponse(
                    pair.accessToken(),
                    pair.rawRefreshToken(),
                    ACCESS_TOKEN_EXPIRY_SECONDS
            );

        } catch (RefreshTokenException e) {
            String message = e.getMessage();

            // Distinguish expired from other invalidity for UX — but
            // do NOT distinguish logout vs replay (information disclosure risk).
            if (message != null && message.contains("expired")) {
                log.debug("Token refresh rejected: expired");
                throw new StashApiException(
                        ErrorCode.AUTH_REFRESH_TOKEN_EXPIRED,
                        "Your session has expired. Please log in again.",
                        HttpStatus.UNAUTHORIZED
                );
            }

            // Covers: not found, revoked (logout), revoked (rotation replay) —
            // all return the same code so no info leaks about why.
            log.debug("Token refresh rejected: invalid or revoked");
            throw new StashApiException(
                    ErrorCode.AUTH_REFRESH_TOKEN_INVALID,
                    "Refresh token is invalid. Please log in again.",
                    HttpStatus.UNAUTHORIZED
            );
        }
    }
}