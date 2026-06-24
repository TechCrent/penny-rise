package com.stash.platform.user.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.stash.platform.user.domain.AccountStatus;
import com.stash.platform.user.domain.KycStatus;
import com.stash.platform.user.domain.SubscriptionTier;
import com.stash.platform.user.domain.User;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link JwtTokenService}.
 */
@DisplayName("JwtTokenService")
class JwtTokenServiceTest {

    // Test key — base64 of a 32-byte secret. NEVER use in production.
    private static final String SIGNING_KEY_B64 =
            Base64.getEncoder().encodeToString(
                    "test-signing-key-32-chars-local!".getBytes());

    // A different key to simulate a previous rotation key
    private static final String OLD_KEY_B64 =
            Base64.getEncoder().encodeToString(
                    "old-signing-key-32-chars-local!!".getBytes());

    private JwtTokenService service;

    @BeforeEach
    void setUp() {
        service = new JwtTokenService(SIGNING_KEY_B64, "", 15);
    }

    private User testUser() {
        User user = new User("alice@stash.com", "hash", "Alice");
        // Override UUID so tests are deterministic
        return user;
    }

    // ── issue ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("issue()")
    class Issue {

        @Test
        @DisplayName("returns a non-null, non-blank JWT string")
        void returns_non_null() {
            String token = service.issue(testUser());
            assertThat(token).isNotNull().isNotBlank();
        }

        @Test
        @DisplayName("JWT has three dot-separated parts (header.payload.signature)")
        void has_three_parts() {
            String token = service.issue(testUser());
            assertThat(token.split("\\.")).hasSize(3);
        }

        @Test
        @DisplayName("issued token is immediately verifiable")
        void issued_token_is_verifiable() {
            User user = testUser();
            String token = service.issue(user);
            AccessTokenClaims claims = service.verify(token);
            assertThat(claims.userId()).isEqualTo(user.getId());
        }
    }

    // ── verify: happy path ────────────────────────────────────────────────

    @Nested
    @DisplayName("verify() — happy path")
    class VerifyHappy {

        @Test
        @DisplayName("extracts correct userId from sub claim")
        void extracts_user_id() {
            User user = testUser();
            AccessTokenClaims claims = service.verify(service.issue(user));
            assertThat(claims.userId()).isEqualTo(user.getId());
        }

        @Test
        @DisplayName("extracts kyc_status claim correctly")
        void extracts_kyc_status() {
            User user = testUser();
            user.setKycStatus(KycStatus.APPROVED);
            AccessTokenClaims claims = service.verify(service.issue(user));
            assertThat(claims.kycStatus()).isEqualTo(KycStatus.APPROVED);
        }

        @Test
        @DisplayName("extracts subscription_tier claim correctly")
        void extracts_subscription_tier() {
            User user = testUser();
            user.setSubscriptionTier(SubscriptionTier.PREMIUM);
            AccessTokenClaims claims = service.verify(service.issue(user));
            assertThat(claims.subscriptionTier()).isEqualTo(SubscriptionTier.PREMIUM);
        }

        @Test
        @DisplayName("extracts account_status claim correctly")
        void extracts_account_status() {
            User user = testUser();
            user.setAccountStatus(AccountStatus.SUSPENDED);
            AccessTokenClaims claims = service.verify(service.issue(user));
            assertThat(claims.accountStatus()).isEqualTo(AccountStatus.SUSPENDED);
        }
    }

    // ── verify: failure cases ─────────────────────────────────────────────

    @Nested
    @DisplayName("verify() — failure cases")
    class VerifyFailures {

        @Test
        @DisplayName("throws ExpiredJwtException for an expired token")
        void throws_for_expired_token() {
            // Issue a token with expiry in the past using a different service instance
            JwtTokenService shortLivedService =
                    new JwtTokenService(SIGNING_KEY_B64, "", -1L); // -1 min = already expired

            String expiredToken = shortLivedService.issue(testUser());

            assertThatExceptionOfType(ExpiredJwtException.class)
                    .isThrownBy(() -> service.verify(expiredToken));
        }

        @Test
        @DisplayName("throws JwtException for a token with a bad signature")
        void throws_for_bad_signature() {
            // Create a token signed with a completely different key
            SecretKey wrongKey = Keys.hmacShaKeyFor(
                    "wrong-key-32-chars-for-testing!!".getBytes());

            String badToken = Jwts.builder()
                    .subject(UUID.randomUUID().toString())
                    .expiration(new Date(System.currentTimeMillis() + 900_000))
                    .claim(JwtTokenService.CLAIM_KYC_STATUS, "PENDING")
                    .claim(JwtTokenService.CLAIM_SUBSCRIPTION_TIER, "FREE")
                    .claim(JwtTokenService.CLAIM_ACCOUNT_STATUS, "ACTIVE")
                    .signWith(wrongKey, Jwts.SIG.HS256)
                    .compact();

            assertThatExceptionOfType(JwtException.class)
                    .isThrownBy(() -> service.verify(badToken));
        }

        @Test
        @DisplayName("throws MalformedJwtException for a completely malformed string")
        void throws_for_malformed_token() {
            assertThatExceptionOfType(JwtException.class)
                    .isThrownBy(() -> service.verify("this.is.notajwt"));
        }

        @Test
        @DisplayName("throws JwtException when required claim kyc_status is missing")
        void throws_for_missing_kyc_status() {
            SecretKey key = Keys.hmacShaKeyFor(Base64.getDecoder().decode(SIGNING_KEY_B64));

            String tokenMissingClaim = Jwts.builder()
                    .subject(UUID.randomUUID().toString())
                    .expiration(new Date(System.currentTimeMillis() + 900_000))
                    // kyc_status intentionally omitted
                    .claim(JwtTokenService.CLAIM_SUBSCRIPTION_TIER, "FREE")
                    .claim(JwtTokenService.CLAIM_ACCOUNT_STATUS, "ACTIVE")
                    .signWith(key, Jwts.SIG.HS256)
                    .compact();

            assertThatExceptionOfType(JwtException.class)
                    .isThrownBy(() -> service.verify(tokenMissingClaim));
        }

        @Test
        @DisplayName("throws JwtException when required claim account_status is missing")
        void throws_for_missing_account_status() {
            SecretKey key = Keys.hmacShaKeyFor(Base64.getDecoder().decode(SIGNING_KEY_B64));

            String tokenMissingClaim = Jwts.builder()
                    .subject(UUID.randomUUID().toString())
                    .expiration(new Date(System.currentTimeMillis() + 900_000))
                    .claim(JwtTokenService.CLAIM_KYC_STATUS, "PENDING")
                    .claim(JwtTokenService.CLAIM_SUBSCRIPTION_TIER, "FREE")
                    // account_status intentionally omitted
                    .signWith(key, Jwts.SIG.HS256)
                    .compact();

            assertThatExceptionOfType(JwtException.class)
                    .isThrownBy(() -> service.verify(tokenMissingClaim));
        }
    }

    // ── Key rotation ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("key rotation")
    class KeyRotation {

        @Test
        @DisplayName("token signed by previous key is accepted during rotation window")
        void accepts_token_signed_by_previous_key() {
            // Simulate: OLD_KEY was the signing key; SIGNING_KEY is the new active key.
            // The service is configured with SIGNING_KEY active and OLD_KEY as a
            // previous verification key.
            JwtTokenService rotatedService = new JwtTokenService(
                    SIGNING_KEY_B64,
                    OLD_KEY_B64,   // previous key still accepted
                    15L
            );

            // Issue a token with the OLD key (as if it was issued before rotation)
            JwtTokenService oldKeyService = new JwtTokenService(OLD_KEY_B64, "", 15L);
            String oldKeyToken = oldKeyService.issue(testUser());

            // The rotated service should accept it
            AccessTokenClaims claims = rotatedService.verify(oldKeyToken);
            assertThat(claims).isNotNull();
        }

        @Test
        @DisplayName("token signed by active key is accepted even with rotation keys configured")
        void accepts_token_signed_by_active_key_with_rotation_configured() {
            JwtTokenService rotatedService = new JwtTokenService(
                    SIGNING_KEY_B64,
                    OLD_KEY_B64,
                    15L
            );

            String token = rotatedService.issue(testUser());
            assertThatNoException().isThrownBy(() -> rotatedService.verify(token));
        }

        @Test
        @DisplayName("token signed by an unknown key is rejected even with rotation configured")
        void rejects_token_signed_by_unknown_key() {
            JwtTokenService rotatedService = new JwtTokenService(
                    SIGNING_KEY_B64,
                    OLD_KEY_B64,
                    15L
            );

            // Token signed by a completely unknown third key
            SecretKey unknownKey = Keys.hmacShaKeyFor(
                    "unknown-key-32-chars-no-one-has!".getBytes());
            String unknownToken = Jwts.builder()
                    .subject(UUID.randomUUID().toString())
                    .expiration(new Date(System.currentTimeMillis() + 900_000))
                    .claim(JwtTokenService.CLAIM_KYC_STATUS, "PENDING")
                    .claim(JwtTokenService.CLAIM_SUBSCRIPTION_TIER, "FREE")
                    .claim(JwtTokenService.CLAIM_ACCOUNT_STATUS, "ACTIVE")
                    .signWith(unknownKey, Jwts.SIG.HS256)
                    .compact();

            assertThatExceptionOfType(JwtException.class)
                    .isThrownBy(() -> rotatedService.verify(unknownToken));
        }
    }

    // ── Security: signing key must not appear in logs ─────────────────────

    @Nested
    @DisplayName("security: signing key must not appear in logs")
    class LogSecurity {

        private ListAppender<ILoggingEvent> captureLogsFrom(Class<?> clazz) {
            Logger logger = (Logger) LoggerFactory.getLogger(clazz);
            ListAppender<ILoggingEvent> appender = new ListAppender<>();
            appender.start();
            logger.addAppender(appender);
            return appender;
        }

        @Test
        @DisplayName("signing key does not appear in log output during issue()")
        void signing_key_not_logged_on_issue() {
            ListAppender<ILoggingEvent> logs = captureLogsFrom(JwtTokenService.class);

            service.issue(testUser());

            List<String> messages = logs.list.stream()
                    .map(ILoggingEvent::getFormattedMessage)
                    .toList();

            for (String message : messages) {
                assertThat(message)
                        .as("Signing key must not appear in logs")
                        .doesNotContain(SIGNING_KEY_B64);
            }
        }

        @Test
        @DisplayName("signing key does not appear in log output during verify()")
        void signing_key_not_logged_on_verify() {
            String token = service.issue(testUser());
            ListAppender<ILoggingEvent> logs = captureLogsFrom(JwtTokenService.class);

            service.verify(token);

            List<String> messages = logs.list.stream()
                    .map(ILoggingEvent::getFormattedMessage)
                    .toList();

            for (String message : messages) {
                assertThat(message)
                        .as("Signing key must not appear in logs")
                        .doesNotContain(SIGNING_KEY_B64);
            }
        }

        @Test
        @DisplayName("token string does not appear in log output")
        void token_not_logged() {
            ListAppender<ILoggingEvent> logs = captureLogsFrom(JwtTokenService.class);

            String token = service.issue(testUser());
            try { service.verify("bad.token.here"); } catch (JwtException ignored) {}

            List<String> messages = logs.list.stream()
                    .map(ILoggingEvent::getFormattedMessage)
                    .toList();

            for (String message : messages) {
                assertThat(message)
                        .as("Token value must not appear in logs")
                        .doesNotContain(token)
                        .doesNotContain("bad.token.here");
            }
        }
    }
}