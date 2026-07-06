package com.stash.kyc.shared.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.UUID;

/**
 * Verifies admin JWTs issued by the monolith's {@code AdminJwtService}.
 * Uses the same shared {@code stash.security.jwt.signing-key} so a token
 * issued by the monolith verifies here without any extra translation —
 * same pattern as audit-service's {@code AdminJwtVerifier}.
 *
 * <p>Replaces {@code PlaceholderAdminAuthFilter}'s shared-secret header
 * check, which stamped every KYC review decision with a sentinel
 * {@code reviewer_admin_id} regardless of which admin was actually logged
 * in. This makes the real admin identity available to
 * {@link com.stash.kyc.api.admin.KycAdminController}.
 */
@Component
public class AdminJwtVerifier {

    private static final String CLAIM_TOKEN_TYPE   = "token_type";
    private static final String CLAIM_ACCOUNT_TYPE = "account_type";
    private static final String CLAIM_ROLE_NAME    = "role_name";
    private static final String TOKEN_TYPE_VALUE   = "ADMIN";

    private final SecretKey signingKey;

    public AdminJwtVerifier(@Value("${stash.security.jwt.signing-key}") String signingKeyBase64) {
        this.signingKey = Keys.hmacShaKeyFor(Base64.getDecoder().decode(signingKeyBase64));
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
