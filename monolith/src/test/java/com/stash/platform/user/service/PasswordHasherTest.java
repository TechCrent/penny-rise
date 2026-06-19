package com.stash.platform.user.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link PasswordHasher}.
 *
 * Uses BCrypt cost 4 for test speed — cost factor 12 would make the
 * test suite take minutes. This is the standard practice; production
 * always uses cost 12 via the application property.
 */
@DisplayName("PasswordHasher")
class PasswordHasherTest {

    // Cost 4 is the minimum BCrypt allows and safe for tests only
    private final PasswordHasher hasher = new PasswordHasher(4);

    // ── hash ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("hash()")
    class Hash {

        @Test
        @DisplayName("produces a non-null, non-blank string")
        void produces_non_null() {
            String hash = hasher.hash("MySecurePassword1!");
            assertThat(hash).isNotNull().isNotBlank();
        }

        @Test
        @DisplayName("produces a valid BCrypt string starting with $2a$")
        void produces_bcrypt_format() {
            String hash = hasher.hash("MySecurePassword1!");
            assertThat(hash).startsWith("$2a$");
        }

        @Test
        @DisplayName("two hashes of the same password differ (random salt)")
        void hashes_are_unique_per_call() {
            String hash1 = hasher.hash("SamePassword123!");
            String hash2 = hasher.hash("SamePassword123!");
            assertThat(hash1).isNotEqualTo(hash2);
        }

        @Test
        @DisplayName("throws IllegalArgumentException for null password")
        void throws_for_null() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> hasher.hash(null));
        }

        @Test
        @DisplayName("throws IllegalArgumentException for blank password")
        void throws_for_blank() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> hasher.hash("   "));
        }
    }

    // ── verify ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("verify()")
    class Verify {

        private String storedHash;

        @BeforeEach
        void setUp() {
            storedHash = hasher.hash("CorrectPassword99!");
        }

        @Test
        @DisplayName("returns true for the correct password")
        void returns_true_for_correct_password() {
            assertThat(hasher.verify("CorrectPassword99!", storedHash)).isTrue();
        }

        @Test
        @DisplayName("returns false for a wrong password")
        void returns_false_for_wrong_password() {
            assertThat(hasher.verify("WrongPassword00!", storedHash)).isFalse();
        }

        @Test
        @DisplayName("returns false for an empty password")
        void returns_false_for_empty() {
            assertThat(hasher.verify("", storedHash)).isFalse();
        }

        @Test
        @DisplayName("returns false when plaintext is null")
        void returns_false_for_null_plaintext() {
            assertThat(hasher.verify(null, storedHash)).isFalse();
        }

        @Test
        @DisplayName("returns false when storedHash is null")
        void returns_false_for_null_hash() {
            assertThat(hasher.verify("CorrectPassword99!", null)).isFalse();
        }

        @Test
        @DisplayName("returns false for a case-sensitive mismatch")
        void case_sensitive() {
            assertThat(hasher.verify("correctpassword99!", storedHash)).isFalse();
        }
    }

    // ── Negative test: raw password must never appear in logs ─────────────

    @Nested
    @DisplayName("security: raw password must not appear in logs")
    class LogSecurity {

        private ListAppender<ILoggingEvent> captureLogsFrom(Class<?> clazz) {
            Logger logger = (Logger) LoggerFactory.getLogger(clazz);
            ListAppender<ILoggingEvent> appender = new ListAppender<>();
            appender.start();
            logger.addAppender(appender);
            return appender;
        }

        @Test
        @DisplayName("hash() does not log the raw password at any level")
        void hash_does_not_log_password() {
            String sensitivePassword = "SuperSecretP@ssword42!";
            ListAppender<ILoggingEvent> logs = captureLogsFrom(PasswordHasher.class);

            hasher.hash(sensitivePassword);

            List<String> logMessages = logs.list.stream()
                    .map(ILoggingEvent::getFormattedMessage)
                    .toList();

            for (String message : logMessages) {
                assertThat(message)
                        .as("Log message should not contain the raw password")
                        .doesNotContain(sensitivePassword);
            }
        }

        @Test
        @DisplayName("verify() does not log the raw password at any level")
        void verify_does_not_log_password() {
            String sensitivePassword = "AnotherSecret#789!";
            String hash = hasher.hash(sensitivePassword);

            ListAppender<ILoggingEvent> logs = captureLogsFrom(PasswordHasher.class);

            hasher.verify(sensitivePassword, hash);
            hasher.verify("WrongAttempt", hash);

            List<String> logMessages = logs.list.stream()
                    .map(ILoggingEvent::getFormattedMessage)
                    .toList();

            for (String message : logMessages) {
                assertThat(message)
                        .as("Log message should not contain the raw password")
                        .doesNotContain(sensitivePassword)
                        .doesNotContain("WrongAttempt");
            }
        }
    }
}