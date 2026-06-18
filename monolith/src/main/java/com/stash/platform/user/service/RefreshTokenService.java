package com.stash.platform.user.service;

import com.stash.platform.user.domain.RefreshToken;
import com.stash.platform.user.domain.User;
import com.stash.platform.user.event.RefreshTokenReplayDetectedEvent;
import com.stash.platform.user.exception.RefreshTokenException;
import com.stash.platform.user.repository.RefreshTokenRepository;
import com.stash.platform.user.repository.UserRepository;
import com.stash.shared.correlation.CorrelationContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Manages refresh token lifecycle: issuance, rotation, revocation, replay detection.
 *
 * <p><strong>Security invariants — never violate:</strong>
 * <ul>
 *   <li>Raw token bytes are NEVER logged, stored, or included in exceptions.</li>
 *   <li>Only the SHA-256 hex hash is persisted in {@code auth.refresh_tokens}.</li>
 *   <li>The raw token is returned to the caller exactly once and then discarded.</li>
 * </ul>
 *
 * <p>See System Design §7.1 and Schema doc §1.1 for the full specification.
 */
@Service
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);

    // Revocation reason constants — must match the CHECK constraint in the migration
    public static final String REASON_ROTATION       = "ROTATION";
    public static final String REASON_ROTATION_REPLAY = "ROTATION_REPLAY";
    public static final String REASON_USER_LOGOUT    = "USER_LOGOUT";
    public static final String REASON_ADMIN_FORCE    = "ADMIN_FORCE";

    private static final int TOKEN_BYTES             = 32;  // 256-bit random token
    private static final long REFRESH_TOKEN_DAYS     = 7L;

    private static final String EXCHANGE_MONOLITH    = "monolith.events";
    private static final String ROUTING_KEY_REPLAY   = "auth.refresh_token.replay_detected";

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final JwtTokenService jwtTokenService;
    private final RabbitTemplate rabbitTemplate;
    private final SecureRandom secureRandom;

    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository,
                               UserRepository userRepository,
                               JwtTokenService jwtTokenService,
                               RabbitTemplate rabbitTemplate) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.userRepository         = userRepository;
        this.jwtTokenService        = jwtTokenService;
        this.rabbitTemplate         = rabbitTemplate;
        this.secureRandom           = new SecureRandom();
    }

    // ── Token pair record ─────────────────────────────────────────────────

    /**
     * A newly issued token pair — access token (JWT) + raw refresh token.
     * The raw refresh token is returned to the client exactly once.
     */
    public record TokenPair(String accessToken, String rawRefreshToken) {}

    // ── Issue (on login) ──────────────────────────────────────────────────

    /**
     * Issues a new token pair at login. The refresh token is stored as
     * a SHA-256 hash; the raw value is returned once to the caller.
     *
     * @param user        the authenticated user
     * @param deviceId    client-provided stable device identifier
     * @param deviceLabel human-readable device name
     * @param ipAddress   client IP at login time
     * @return a token pair (access JWT + raw refresh token)
     */
    @Transactional
    public TokenPair issue(User user, String deviceId, String deviceLabel, String ipAddress) {
        byte[] rawBytes = generateRawToken();
        String rawToken = encodeToken(rawBytes);       // DO NOT log rawToken
        String hash     = sha256Hex(rawBytes);         // DO NOT log hash

        RefreshToken stored = new RefreshToken(
                user.getId(),
                hash,
                deviceId,
                deviceLabel,
                ipAddress,
                Instant.now().plus(REFRESH_TOKEN_DAYS, ChronoUnit.DAYS)
        );
        refreshTokenRepository.save(stored);

        String accessToken = jwtTokenService.issue(user);

        log.info("Refresh token issued for user={} device={}",
                user.getId(), deviceId);
        // Note: rawToken is NOT logged above.

        return new TokenPair(accessToken, rawToken);
    }

    // ── Refresh (token rotation) ──────────────────────────────────────────

    /**
     * Exchanges a valid raw refresh token for a new token pair.
     *
     * <p>The exchange is atomic: the old token is revoked and the new
     * token is inserted in the same transaction.
     *
     * @param rawRefreshToken the raw token from the client
     * @param deviceId        client's device ID (carried forward)
     * @param deviceLabel     client's device label
     * @param ipAddress       client's current IP
     * @return a new token pair
     * @throws RefreshTokenException if the token is invalid, expired, or revoked
     */
    @Transactional
    public TokenPair refresh(String rawRefreshToken, String deviceId,
                             String deviceLabel, String ipAddress) {
        // DO NOT log rawRefreshToken
        String hash = sha256Hex(rawRefreshToken.getBytes(StandardCharsets.UTF_8));
        RefreshToken existing = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new RefreshTokenException("Refresh token not found"));

        if (existing.isExpired()) {
            log.debug("Refresh rejected: token expired for user={}", existing.getUserId());
            throw new RefreshTokenException("Refresh token has expired");
        }

        if (existing.isRevoked()) {
            // This is a replay attack — walk the chain and revoke all descendants
            handleReplayAttack(existing);
            throw new RefreshTokenException("Refresh token has been revoked — possible replay attack detected");
        }

        // Token is valid — rotate it
        User user = userRepository.findByIdIncludingDeleted(existing.getUserId())
                .orElseThrow(() -> new RefreshTokenException("User not found for refresh token"));

        // Issue new token
        byte[] newRawBytes  = generateRawToken();
        String newRawToken  = encodeToken(newRawBytes);
        String newHash      = sha256Hex(newRawBytes);

        RefreshToken newToken = new RefreshToken(
                user.getId(),
                newHash,
                deviceId,
                deviceLabel,
                ipAddress,
                Instant.now().plus(REFRESH_TOKEN_DAYS, ChronoUnit.DAYS)
        );
        refreshTokenRepository.save(newToken);

        // Revoke the old token, linking to the new one
        existing.revoke(REASON_ROTATION);
        existing.setReplacedById(newToken.getId());
        existing.setLastUsedAt(Instant.now());
        refreshTokenRepository.save(existing);

        String newAccessToken = jwtTokenService.issue(user);

        log.info("Refresh token rotated for user={} device={}", user.getId(), deviceId);

        return new TokenPair(newAccessToken, newRawToken);
    }

    // ── Revoke (logout) ───────────────────────────────────────────────────

    /**
     * Revokes a single refresh token (user-initiated logout).
     * Idempotent: already-revoked tokens are accepted without error.
     *
     * @param rawRefreshToken the raw token to revoke
     */
    @Transactional
    public void revoke(String rawRefreshToken) {
        String hash = sha256Hex(rawRefreshToken.getBytes(StandardCharsets.UTF_8));
        refreshTokenRepository.findByTokenHash(hash).ifPresent(token -> {
            if (!token.isRevoked()) {
                token.revoke(REASON_USER_LOGOUT);
                refreshTokenRepository.save(token);
                log.info("Refresh token revoked (logout) for user={}", token.getUserId());
            }
        });
    }

    /**
     * Revokes ALL active refresh tokens for a user (e.g. after password reset).
     *
     * @param userId the user whose sessions to invalidate
     */
    @Transactional
    public void revokeAllForUser(UUID userId, String reason) {
        List<RefreshToken> active = refreshTokenRepository.findActiveByUserId(userId);
        Instant now = Instant.now();
        for (RefreshToken token : active) {
            token.revoke(reason);
        }
        refreshTokenRepository.saveAll(active);
        log.info("Revoked {} refresh tokens for user={} reason={}", active.size(), userId, reason);
    }

    // ── Replay attack handling ────────────────────────────────────────────

    /**
     * Handles a replay attack: walks the replaced_by_id chain forward from
     * the replayed token and revokes every descendant in a single transaction.
     *
     * <p>All revocations happen before this method returns, within the
     * calling transaction. No partial revocations are possible.
     *
     * @param replayedToken the token that was presented after already being rotated
     */
    private void handleReplayAttack(RefreshToken replayedToken) {
        log.warn("REPLAY ATTACK DETECTED: user={} replayedTokenId={}",
                replayedToken.getUserId(), replayedToken.getId());

        int revokedCount = revokeChainFrom(replayedToken.getId());

        log.warn("Replay response complete: revoked {} descendant tokens for user={}",
                revokedCount, replayedToken.getUserId());

        // Emit event for notification worker (v0.5)
        publishReplayEvent(replayedToken.getUserId(), replayedToken.getId(), revokedCount);
    }

    /**
     * Recursively walks the rotation chain starting from {@code parentId}
     * and revokes every descendant with reason ROTATION_REPLAY.
     *
     * @return the number of tokens revoked
     */
    private int revokeChainFrom(UUID parentId) {
        List<RefreshToken> descendants = refreshTokenRepository.findByReplacedById(parentId);
        int count = 0;

        for (RefreshToken descendant : descendants) {
            if (!descendant.isRevoked()) {
                descendant.revoke(REASON_ROTATION_REPLAY);
                refreshTokenRepository.save(descendant);
                count++;
            }
            // Walk deeper into the chain
            count += revokeChainFrom(descendant.getId());
        }

        return count;
    }

    private void publishReplayEvent(UUID userId, UUID replayedTokenId, int descendantsRevoked) {
        try {
            var event = new RefreshTokenReplayDetectedEvent(
                    UUID.randomUUID().toString(),
                    RefreshTokenReplayDetectedEvent.EVENT_TYPE,
                    RefreshTokenReplayDetectedEvent.SCHEMA_VERSION,
                    RefreshTokenReplayDetectedEvent.SOURCE_SERVICE,
                    Instant.now(),
                    CorrelationContext.get(),
                    new RefreshTokenReplayDetectedEvent.Payload(
                            userId, replayedTokenId, descendantsRevoked)
            );
            rabbitTemplate.convertAndSend(EXCHANGE_MONOLITH, ROUTING_KEY_REPLAY, event);
            log.info("Replay detection event published for user={}", userId);
        } catch (Exception e) {
            // Non-fatal: the revocations already happened. Log and continue.
            log.error("Failed to publish replay detection event for user={}: {}",
                    userId, e.getMessage());
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────

    private byte[] generateRawToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return bytes;
    }

    private static String encodeToken(byte[] rawBytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(rawBytes);
    }

    private static String sha256Hex(byte[] input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(input);
            StringBuilder sb = new StringBuilder(hashBytes.length * 2);
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private static String sha256Hex(String input) {
        return sha256Hex(input.getBytes(StandardCharsets.UTF_8));
    }
}