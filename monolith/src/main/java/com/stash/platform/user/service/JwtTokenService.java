package com.stash.platform.user.service;

import com.stash.platform.user.domain.AccountStatus;
import com.stash.platform.user.domain.KycStatus;
import com.stash.platform.user.domain.SubscriptionTier;
import com.stash.platform.user.domain.User;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Issues and verifies JWT access tokens per System Design §7.1.
 *
 * <h2>Token format</h2>
 * <p>HS256-signed JWT. Claims:
 * <ul>
 *   <li>{@code sub} — user UUID (string)</li>
 *   <li>{@code iat} — issued-at (epoch seconds)</li>
 *   <li>{@code exp} — expiry (iat + 15 minutes)</li>
 *   <li>{@code kyc_status} — string enum value</li>
 *   <li>{@code subscription_tier} — string enum value</li>
 *   <li>{@code account_status} — string enum value</li>
 * </ul>
 *
 * <h2>Key rotation</h2>
 * <p>One active signing key is used to issue new tokens. Zero or more
 * previous verification keys are accepted during the rotation window.
 * The verifier tries all keys (signing + verification) in order until one
 * succeeds or all fail. This allows quarterly key rotation without
 * invalidating sessions that hold tokens signed by the previous key
 * (which has a 15-minute lifetime anyway, making the overlap trivial).
 *
 * <h2>Security invariants</h2>
 * <ul>
 *   <li>Signing key bytes are NEVER logged at any level.</li>
 *   <li>Tokens are NEVER logged in full (they contain user identity).</li>
 *   <li>Verification failures are logged at DEBUG without the token value.</li>
 * </ul>
 */
@Service
public class JwtTokenService {

    private static final Logger log = LoggerFactory.getLogger(JwtTokenService.class);

    // Custom claim names — must match what the JWT filter reads
    static final String CLAIM_KYC_STATUS         = "kyc_status";
    static final String CLAIM_SUBSCRIPTION_TIER  = "subscription_tier";
    static final String CLAIM_ACCOUNT_STATUS     = "account_status";

    private final SecretKey signingKey;
    private final List<SecretKey> allVerificationKeys; // signing key + previous keys
    private final long accessTokenExpiryMinutes;

    public JwtTokenService(
            @Value("${stash.security.jwt.signing-key}") String signingKeyBase64,
            @Value("${stash.security.jwt.verification-keys:}") String verificationKeysBase64,
            @Value("${stash.security.jwt.access-token-expiry-minutes:15}") long accessTokenExpiryMinutes) {

        // DO NOT log signingKeyBase64 or verificationKeysBase64
        this.signingKey = Keys.hmacShaKeyFor(Base64.getDecoder().decode(signingKeyBase64));
        this.accessTokenExpiryMinutes = accessTokenExpiryMinutes;

        // Build the full list of keys the verifier will try: active signing key first,
        // then any previous rotation keys
        List<SecretKey> keys = new ArrayList<>();
        keys.add(this.signingKey);

        if (verificationKeysBase64 != null && !verificationKeysBase64.isBlank()) {
            for (String keyBase64 : verificationKeysBase64.split(",")) {
                String trimmed = keyBase64.trim();
                if (!trimmed.isEmpty()) {
                    keys.add(Keys.hmacShaKeyFor(Base64.getDecoder().decode(trimmed)));
                }
            }
        }

        this.allVerificationKeys = Collections.unmodifiableList(keys);
        log.info("JwtTokenService initialised: {} verification key(s), expiry {} min",
                allVerificationKeys.size(), accessTokenExpiryMinutes);
        // Note: key count logged, not key material.
    }

    // ── Issue ─────────────────────────────────────────────────────────────

    /**
     * Issues a signed JWT access token for the given user.
     *
     * @param user the authenticated user — must not be null
     * @return a compact JWT string
     */
    public String issue(User user) {
        Instant now    = Instant.now();
        Instant expiry = now.plus(accessTokenExpiryMinutes, ChronoUnit.MINUTES);

        return Jwts.builder()
                .subject(user.getId().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .claim(CLAIM_KYC_STATUS,        user.getKycStatus().name())
                .claim(CLAIM_SUBSCRIPTION_TIER, user.getSubscriptionTier().name())
                .claim(CLAIM_ACCOUNT_STATUS,    user.getAccountStatus().name())
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
        // DO NOT log the returned token string.
    }

    // ── Verify ────────────────────────────────────────────────────────────

    /**
     * Parses and verifies a JWT access token.
     *
     * <p>Tries all verification keys in order. If any key validates the token,
     * the claims are extracted and returned. This enables seamless key rotation.
     *
     * @param token the compact JWT string from the Authorization header
     * @return parsed {@link AccessTokenClaims} if the token is valid
     * @throws JwtException if the token is expired, malformed, or has an invalid signature
     */
    public AccessTokenClaims verify(String token) {
        // DO NOT log the token parameter.
        JwtException lastException = null;

        for (SecretKey key : allVerificationKeys) {
            try {
                Jws<Claims> jws = Jwts.parser()
                        .verifyWith(key)
                        .build()
                        .parseSignedClaims(token);

                return extractClaims(jws);

            } catch (ExpiredJwtException e) {
                // Expiry is definitive — no need to try other keys
                log.debug("JWT verification failed: token expired");
                throw e;

            } catch (JwtException e) {
                // Wrong key or malformed — try the next key
                lastException = e;
            }
        }

        log.debug("JWT verification failed: no key accepted the token");
        throw Objects.requireNonNull(lastException);
    }

    // ── Private helpers ───────────────────────────────────────────────────

    private AccessTokenClaims extractClaims(Jws<Claims> jws) {
        Claims claims = jws.getPayload();

        // Two-way rejection: admin tokens share the same signing key but must never
        // authenticate customer endpoints. Admin tokens carry token_type=ADMIN;
        // customer tokens have no token_type claim. This explicit check is defense in
        // depth — admin tokens also lack kyc_status/subscription_tier so the claim
        // extraction below would throw anyway, but explicit is safer and self-documenting.
        if ("ADMIN".equals(claims.get("token_type"))) {
            throw new MalformedJwtException(
                    "Admin token rejected on customer endpoint (token_type=ADMIN)");
        }

        String sub = claims.getSubject();
        if (sub == null || sub.isBlank()) {
            throw new MissingClaimException(jws.getHeader(), claims, "sub", null,
                    "Missing required claim: sub");
        }

        String kycStatusStr        = requireClaim(jws, CLAIM_KYC_STATUS);
        String subscriptionTierStr = requireClaim(jws, CLAIM_SUBSCRIPTION_TIER);
        String accountStatusStr    = requireClaim(jws, CLAIM_ACCOUNT_STATUS);

        try {
            return new AccessTokenClaims(
                    UUID.fromString(sub),
                    KycStatus.valueOf(kycStatusStr),
                    SubscriptionTier.valueOf(subscriptionTierStr),
                    AccountStatus.valueOf(accountStatusStr)
            );
        } catch (IllegalArgumentException e) {
            throw new MalformedJwtException("Invalid enum value in JWT claims: " + e.getMessage());
        }
    }

    private String requireClaim(Jws<Claims> jws, String claimName) {
        Claims claims = jws.getPayload();
        Object value = claims.get(claimName);
        if (value == null) {
            throw new MissingClaimException(jws.getHeader(), claims, claimName, null,
                    "Missing required claim: " + claimName);
        }
        return value.toString();
    }
}