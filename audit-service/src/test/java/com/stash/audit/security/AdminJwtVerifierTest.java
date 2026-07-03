package com.stash.audit.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * v0.5-033: this class had no direct test coverage before — the real bug
 * (wrong property name, wrong byte encoding, meaning monolith-issued
 * tokens could never actually verify here) was only caught by reading
 * both services' source directly, not by any test failing. These tests
 * simulate exactly how monolith's AdminJwtService signs a token (Base64
 * key, HS256) to prove AdminJwtVerifier can genuinely interoperate with
 * it, not just parse tokens it signs itself.
 */
class AdminJwtVerifierTest {

    // A real Base64-encoded 256-bit key, same format monolith's
    // AdminJwtService expects for stash.security.jwt.signing-key.
    private static final String SIGNING_KEY_BASE64 =
            Base64.getEncoder().encodeToString("a-test-signing-key-that-is-long-enough-for-hs256".getBytes());

    private final AdminJwtVerifier verifier = new AdminJwtVerifier(SIGNING_KEY_BASE64);

    private String signTokenLikeMonolith(String tokenType, String accountType, String roleName, UUID adminAccountId) {
        SecretKey key = Keys.hmacShaKeyFor(Base64.getDecoder().decode(SIGNING_KEY_BASE64));
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(adminAccountId.toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(Duration.ofMinutes(5))))
                .claims(Map.of("token_type", tokenType, "account_type", accountType, "role_name", roleName))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    @Test
    @DisplayName("verifies a token signed exactly like monolith's AdminJwtService (Base64 key, HS256) — the real interop case")
    void verifiesMonolithStyleToken() {
        UUID adminId = UUID.randomUUID();
        String token = signTokenLikeMonolith("ADMIN", "SUPER", "SUPER", adminId);

        var claims = verifier.parseAndValidate(token);

        assertThat(claims.adminAccountId()).isEqualTo(adminId);
        assertThat(claims.accountType()).isEqualTo("SUPER");
        assertThat(claims.roleName()).isEqualTo("SUPER");
    }

    @Test
    @DisplayName("rejects a token whose token_type is not ADMIN")
    void rejectsNonAdminToken() {
        String token = signTokenLikeMonolith("CUSTOMER", "N/A", "", UUID.randomUUID());

        assertThatThrownBy(() -> verifier.parseAndValidate(token))
                .isInstanceOf(io.jsonwebtoken.security.SecurityException.class);
    }

    @Test
    @DisplayName("rejects a token signed with a different key (signature mismatch)")
    void rejectsTokenSignedWithDifferentKey() {
        SecretKey wrongKey = Keys.hmacShaKeyFor(
                Base64.getDecoder().decode(Base64.getEncoder().encodeToString(
                        "a-completely-different-signing-key-value".getBytes())));
        Instant now = Instant.now();
        String token = Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(Duration.ofMinutes(5))))
                .claims(Map.of("token_type", "ADMIN", "account_type", "SUPER", "role_name", "SUPER"))
                .signWith(wrongKey, Jwts.SIG.HS256)
                .compact();

        assertThatThrownBy(() -> verifier.parseAndValidate(token))
                .isInstanceOf(io.jsonwebtoken.security.SignatureException.class);
    }
}
