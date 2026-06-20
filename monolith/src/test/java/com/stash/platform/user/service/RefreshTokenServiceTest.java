package com.stash.platform.user.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.stash.platform.user.domain.RefreshToken;
import com.stash.platform.user.domain.User;
import com.stash.platform.user.exception.RefreshTokenException;
import com.stash.platform.user.repository.RefreshTokenRepository;
import com.stash.platform.user.repository.UserRepository;
import org.junit.jupiter.api.*;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("RefreshTokenService")
class RefreshTokenServiceTest {

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16")
                    .withDatabaseName("monolith_test")
                    .withUsername("test")
                    .withPassword("test");

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",      postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired RefreshTokenService service;
    @Autowired RefreshTokenRepository tokenRepo;
    @Autowired UserRepository userRepo;
    @Autowired JdbcTemplate jdbcTemplate;
    @MockBean  RabbitTemplate rabbitTemplate; // don't need a real broker for these tests

    private User savedUser;

    @BeforeEach
    void setUp() {
        tokenRepo.deleteAll();
        userRepo.deleteAll();
        savedUser = userRepo.save(new User("test@stash.com", "hash", "Test User"));
    }

    // ── issue ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("issue()")
    class Issue {

        @Test
        @DisplayName("returns a non-null token pair")
        void returns_token_pair() {
            var pair = service.issue(savedUser, "device-1", "iPhone", "127.0.0.1");
            assertThat(pair.accessToken()).isNotBlank();
            assertThat(pair.rawRefreshToken()).isNotBlank();
        }

        @Test
        @DisplayName("persists token as SHA-256 hash, not plaintext")
        void persists_hash_not_plaintext() {
            var pair = service.issue(savedUser, "device-1", "iPhone", "127.0.0.1");
            List<RefreshToken> all = tokenRepo.findAll();
            assertThat(all).hasSize(1);
            // The stored hash must not equal the raw token
            assertThat(all.get(0).getTokenHash()).isNotEqualTo(pair.rawRefreshToken());
            // The raw token must not be in the DB at all
            assertThat(all.get(0).getTokenHash()).doesNotContain(pair.rawRefreshToken());
        }
    }

    // ── refresh (happy path) ──────────────────────────────────────────────

    @Nested
    @DisplayName("refresh() — happy path")
    class RefreshHappy {

        @Test
        @DisplayName("returns a new token pair on valid token")
        void returns_new_pair() {
            var original = service.issue(savedUser, "device-1", "iPhone", "127.0.0.1");
            var refreshed = service.refresh(
                    original.rawRefreshToken(), "device-1", "iPhone", "127.0.0.1");

            assertThat(refreshed.accessToken()).isNotBlank();
            assertThat(refreshed.rawRefreshToken()).isNotBlank();
            assertThat(refreshed.rawRefreshToken())
                    .isNotEqualTo(original.rawRefreshToken());
        }

        @Test
        @DisplayName("old token is revoked with ROTATION reason after refresh")
        void old_token_revoked_after_refresh() {
            var original = service.issue(savedUser, "device-1", "iPhone", "127.0.0.1");
            service.refresh(original.rawRefreshToken(), "device-1", "iPhone", "127.0.0.1");

            List<RefreshToken> tokens = tokenRepo.findAll();
            RefreshToken revoked = tokens.stream()
                    .filter(RefreshToken::isRevoked)
                    .findFirst()
                    .orElseThrow();

            assertThat(revoked.getRevokedReason()).isEqualTo(RefreshTokenService.REASON_ROTATION);
            assertThat(revoked.getReplacedById()).isNotNull();
        }

        @Test
        @DisplayName("two tokens exist after one rotation (old revoked + new active)")
        void two_tokens_after_rotation() {
            var original = service.issue(savedUser, "device-1", "iPhone", "127.0.0.1");
            service.refresh(original.rawRefreshToken(), "device-1", "iPhone", "127.0.0.1");

            assertThat(tokenRepo.findAll()).hasSize(2);
        }
    }

    // ── refresh: failure cases ────────────────────────────────────────────

    @Nested
    @DisplayName("refresh() — failure cases")
    class RefreshFailures {

        @Test
        @DisplayName("throws RefreshTokenException for unknown token")
        void throws_for_unknown_token() {
            assertThatExceptionOfType(RefreshTokenException.class)
                    .isThrownBy(() -> service.refresh(
                            "completely-unknown-token", "d1", "Phone", "127.0.0.1"));
        }

        @Test
        @DisplayName("throws RefreshTokenException for expired token")
        void throws_for_expired_token() {
            var pair = service.issue(savedUser, "d1", "Phone", "127.0.0.1");
            // expires_at is updatable=false on the entity — use SQL to force expiry
            jdbcTemplate.update(
                    "UPDATE auth.refresh_tokens SET expires_at = ? WHERE token_hash = ?",
                    java.sql.Timestamp.from(Instant.now().minusSeconds(60)),
                    sha256Hex(pair.rawRefreshToken()));

            assertThatExceptionOfType(RefreshTokenException.class)
                    .isThrownBy(() -> service.refresh(
                            pair.rawRefreshToken(), "d1", "Phone", "127.0.0.1"));
        }

        @Test
        @DisplayName("throws RefreshTokenException for a directly-revoked token")
        void throws_for_revoked_token() {
            var pair = service.issue(savedUser, "d1", "Phone", "127.0.0.1");
            service.revoke(pair.rawRefreshToken());

            assertThatExceptionOfType(RefreshTokenException.class)
                    .isThrownBy(() -> service.refresh(
                            pair.rawRefreshToken(), "d1", "Phone", "127.0.0.1"));
        }
    }

    // ── Replay detection ──────────────────────────────────────────────────

    @Nested
    @DisplayName("replay detection")
    class ReplayDetection {

        @Test
        @DisplayName("presenting a rotated token triggers chain revocation")
        void replay_triggers_chain_revocation() {
            // Setup: issue → rotate once → rotate again (3 tokens: T0, T1, T2)
            var t0 = service.issue(savedUser, "d1", "Phone", "127.0.0.1");
            var t1 = service.refresh(t0.rawRefreshToken(), "d1", "Phone", "127.0.0.1");
            service.refresh(t1.rawRefreshToken(), "d1", "Phone", "127.0.0.1");

            // T0 was rotated; T1 was rotated; T2 is the current active token
            // Now replay T0 (the very first token that was already rotated)
            assertThatExceptionOfType(RefreshTokenException.class)
                    .isThrownBy(() -> service.refresh(
                            t0.rawRefreshToken(), "d1", "Phone", "127.0.0.1"));

            // All tokens should now be revoked
            List<RefreshToken> all = tokenRepo.findAll();
            assertThat(all).hasSize(3);
            assertThat(all).allMatch(RefreshToken::isRevoked);
        }

        @Test
        @DisplayName("descendants are revoked with ROTATION_REPLAY reason")
        void descendants_revoked_with_replay_reason() {
            var t0 = service.issue(savedUser, "d1", "Phone", "127.0.0.1");
            var t1 = service.refresh(t0.rawRefreshToken(), "d1", "Phone", "127.0.0.1");
            service.refresh(t1.rawRefreshToken(), "d1", "Phone", "127.0.0.1");

            try {
                service.refresh(t0.rawRefreshToken(), "d1", "Phone", "127.0.0.1");
            } catch (RefreshTokenException ignored) {}

            List<RefreshToken> replayRevoked = tokenRepo.findAll().stream()
                    .filter(t -> RefreshTokenService.REASON_ROTATION_REPLAY
                            .equals(t.getRevokedReason()))
                    .toList();

            // T1 and T2 should be revoked as ROTATION_REPLAY
            assertThat(replayRevoked).hasSizeGreaterThanOrEqualTo(1);
        }

        @Test
        @DisplayName("replay event published to RabbitMQ")
        void replay_event_published() {
            var t0 = service.issue(savedUser, "d1", "Phone", "127.0.0.1");
            service.refresh(t0.rawRefreshToken(), "d1", "Phone", "127.0.0.1");

            try {
                service.refresh(t0.rawRefreshToken(), "d1", "Phone", "127.0.0.1");
            } catch (RefreshTokenException ignored) {}

            verify(rabbitTemplate, atLeastOnce())
                    .convertAndSend(anyString(), anyString(), any(Object.class));
        }
    }

    // ── Security: raw token must not appear in logs ───────────────────────

    @Nested
    @DisplayName("security: raw token must not appear in logs")
    class LogSecurity {

        @Test
        @DisplayName("raw refresh token does not appear in any log during issue or refresh")
        void raw_token_not_in_logs() {
            Logger logger = (Logger) LoggerFactory.getLogger(RefreshTokenService.class);
            ListAppender<ILoggingEvent> appender = new ListAppender<>();
            appender.start();
            logger.addAppender(appender);

            var pair = service.issue(savedUser, "d1", "Phone", "127.0.0.1");
            service.refresh(pair.rawRefreshToken(), "d1", "Phone", "127.0.0.1");

            List<String> messages = appender.list.stream()
                    .map(ILoggingEvent::getFormattedMessage)
                    .toList();

            for (String message : messages) {
                assertThat(message)
                        .as("Raw token must not appear in logs")
                        .doesNotContain(pair.rawRefreshToken());
            }

            logger.detachAppender(appender);
        }
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hashBytes.length * 2);
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}