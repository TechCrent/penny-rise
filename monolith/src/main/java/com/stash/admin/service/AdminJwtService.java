package com.stash.admin.service;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Issues and validates admin access tokens (JWTs).
 *
 * <p><strong>Critical claim: {@code token_type = "ADMIN"}.</strong> This is
 * what makes the two-way rejection between admin and user tokens possible.
 * Both token families are signed with HS256 using the same shared signing
 * key (per System Design: "shared across all backend services so they can
 * validate each other's tokens"), so signature validity alone cannot
 * distinguish an admin token from a customer token. The {@code token_type}
 * claim is the actual discriminator: the admin filter requires
 * {@code token_type == "ADMIN"} and rejects anything else; the customer
 * {@code JwtTokenService.extractClaims} checks for and explicitly rejects
 * any token that has {@code token_type == "ADMIN"} before processing it as
 * a customer token.
 */
@Component
public class AdminJwtService {

    private static final String CLAIM_TOKEN_TYPE   = "token_type";
    private static final String CLAIM_ACCOUNT_TYPE = "account_type";
    private static final String CLAIM_ROLE_NAME    = "role_name";
    private static final String TOKEN_TYPE_VALUE   = "ADMIN";

    static final Duration ACCESS_TOKEN_TTL = Duration.ofMinutes(5);

    private final SecretKey signingKey;
    private final Clock     clock;

    public AdminJwtService(
            @Value("${stash.security.jwt.signing-key}") String signingKeyBase64,
            Clock clock) {
        // Same shared signing key as customer tokens — per System Design, the
        // key is shared across services. Discrimination happens via the
        // token_type claim, not via a separate key. Base64-decoded to match
        // the same format used by JwtTokenService.
        this.signingKey = Keys.hmacShaKeyFor(Base64.getDecoder().decode(signingKeyBase64));
        this.clock      = clock;
    }

    public String issueAccessToken(UUID adminAccountId, String accountType, String roleName) {
        Instant now = Instant.now(clock);
        Instant exp = now.plus(ACCESS_TOKEN_TTL);

        Map<String, Object> claims = new HashMap<>();
        claims.put(CLAIM_TOKEN_TYPE,   TOKEN_TYPE_VALUE);
        claims.put(CLAIM_ACCOUNT_TYPE, accountType);
        claims.put(CLAIM_ROLE_NAME,    roleName != null ? roleName : "");

        return Jwts.builder()
                .subject(adminAccountId.toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .claims(claims)
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * Parses and validates an admin token. Throws if the signature is
     * invalid, the token is expired, OR {@code token_type != "ADMIN"}.
     * The third check is what makes this unusable as a customer token
     * even though the signature would otherwise verify fine.
     */
    public AdminTokenClaims parseAndValidate(String token) {
        var jws = Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token);

        var claims = jws.getPayload();
        String tokenType = claims.get(CLAIM_TOKEN_TYPE, String.class);

        if (!TOKEN_TYPE_VALUE.equals(tokenType)) {
            throw new MalformedJwtException(
                    "Token is not an admin token (token_type=" + tokenType + ")");
        }

        return new AdminTokenClaims(
                UUID.fromString(claims.getSubject()),
                claims.get(CLAIM_ACCOUNT_TYPE, String.class),
                claims.get(CLAIM_ROLE_NAME, String.class)
        );
    }

    public record AdminTokenClaims(UUID adminAccountId, String accountType, String roleName) {}
}
