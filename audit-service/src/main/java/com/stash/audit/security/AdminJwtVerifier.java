package com.stash.audit.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.UUID;

/**
 * PROMOTE-TO-SHARED CANDIDATE — near-duplicate of the monolith's
 * AdminJwtService verification half (v0.5-003), minus token issuance.
 * Uses the same shared signing secret so cross-service verification works.
 */
@Component
public class AdminJwtVerifier {

    private static final String CLAIM_TOKEN_TYPE  = "token_type";
    private static final String CLAIM_ACCOUNT_TYPE = "account_type";
    private static final String CLAIM_ROLE_NAME    = "role_name";
    private static final String TOKEN_TYPE_VALUE   = "ADMIN";

    private final SecretKey signingKey;

    public AdminJwtVerifier(@Value("${stash.jwt.signing-secret}") String signingSecret) {
        this.signingKey = Keys.hmacShaKeyFor(signingSecret.getBytes());
    }

    public AdminTokenClaims parseAndValidate(String token) {
        var jws    = Jwts.parser().verifyWith(signingKey).build().parseSignedClaims(token);
        var claims = jws.getPayload();

        String tokenType = claims.get(CLAIM_TOKEN_TYPE, String.class);
        if (!TOKEN_TYPE_VALUE.equals(tokenType)) {
            throw new io.jsonwebtoken.security.SecurityException(
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
