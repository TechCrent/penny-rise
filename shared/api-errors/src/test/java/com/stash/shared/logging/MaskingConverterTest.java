package com.stash.shared.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.classic.spi.ILoggingEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("MaskingConverter")
class MaskingConverterTest {

    private final MaskingConverter converter = new MaskingConverter();

    private ILoggingEvent event(String message) {
        Logger logger = (Logger) LoggerFactory.getLogger(MaskingConverterTest.class);
        LoggingEvent event = new LoggingEvent(
                "FQCN", logger, Level.INFO, message, null, null);
        return event;
    }

    @Test
    @DisplayName("JSON password field is redacted")
    void json_password_redacted() {
        String msg = "{\"email\":\"user@stash.com\",\"password\":\"secret123\"}";
        String result = converter.convert(event(msg));
        assertThat(result).doesNotContain("secret123");
        assertThat(result).contains("[REDACTED]");
        assertThat(result).contains("user@stash.com"); // other fields preserved
    }

    @Test
    @DisplayName("JSON token field is redacted")
    void json_token_redacted() {
        String msg = "{\"access_token\":\"eyJhbGciOiJIUzI1NiJ9.payload.sig\"}";
        String result = converter.convert(event(msg));
        assertThat(result).doesNotContain("eyJhbGciOiJIUzI1NiJ9");
        assertThat(result).contains("[REDACTED]");
    }

    @Test
    @DisplayName("JSON ghana_card_number field is redacted")
    void json_ghana_card_number_redacted() {
        String msg = "{\"ghana_card_number\":\"GHA-123456789-0\",\"user_id\":\"abc\"}";
        String result = converter.convert(event(msg));
        assertThat(result).doesNotContain("GHA-123456789-0");
        assertThat(result).contains("[REDACTED]");
        assertThat(result).contains("abc"); // user_id preserved
    }

    @Test
    @DisplayName("plain text password= pattern is redacted")
    void plain_text_password_redacted() {
        String msg = "User login attempt password=hunter2 email=user@stash.com";
        String result = converter.convert(event(msg));
        assertThat(result).doesNotContain("hunter2");
        assertThat(result).contains("[REDACTED]");
        assertThat(result).contains("user@stash.com");
    }

    @Test
    @DisplayName("safe fields are not redacted")
    void safe_fields_pass_through() {
        String msg = "{\"user_id\":\"abc-123\",\"vault_id\":\"xyz-789\",\"amount\":5000}";
        String result = converter.convert(event(msg));
        assertThat(result).isEqualTo(msg); // unchanged
    }

    @Test
    @DisplayName("null message does not throw")
    void null_message_safe() {
        Logger logger = (Logger) LoggerFactory.getLogger(MaskingConverterTest.class);
        LoggingEvent event = new LoggingEvent("FQCN", logger, Level.INFO, null, null, null);
        assertThat(converter.convert(event)).isEmpty();
    }

    @Test
    @DisplayName("case-insensitive match — Password field redacted")
    void case_insensitive() {
        String msg = "{\"Password\":\"SuperSecret\"}";
        String result = converter.convert(event(msg));
        assertThat(result).doesNotContain("SuperSecret");
    }
}
