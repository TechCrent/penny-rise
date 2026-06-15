package com.stash.shared.logging;

import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Logback message converter that redacts sensitive field values before
 * they reach the JSON encoder.
 *
 * <p>Blocked fields per System Design §8.1 and the KYC data-handling policy:
 * <ul>
 *   <li>{@code password} — user or admin passwords in any form</li>
 *   <li>{@code token} — access tokens, refresh tokens, OTP tokens</li>
 *   <li>{@code ghana_card_number} — NIA card number (PII, regulatory)</li>
 * </ul>
 *
 * <p>The redaction pattern matches JSON key-value pairs. Values are replaced
 * with {@code [REDACTED]} regardless of surrounding quote style.
 *
 * <p>Register in {@code logback-spring.xml}:
 * <pre>
 *   &lt;conversionRule conversionWord="maskedMsg"
 *       converterClass="com.stash.shared.logging.MaskingConverter"/&gt;
 *   ...
 *   &lt;pattern&gt;%maskedMsg&lt;/pattern&gt;
 * </pre>
 */
public class MaskingConverter extends ClassicConverter {

    private static final String REDACTED = "[REDACTED]";

    /**
     * Patterns that match JSON-serialised sensitive fields.
     * Matches: "password":"anyvalue" or "password": "anyvalue"
     */
    private static final List<Pattern> SENSITIVE_PATTERNS = List.of(
            // JSON key-value: "password":"value" or "password" : "value"
            Pattern.compile(
                    "(?i)(\"(?:password|passwd|pwd)\"\\s*:\\s*\")([^\"]*)(\")",
                    Pattern.CASE_INSENSITIVE),
            Pattern.compile(
                    "(?i)(\"(?:token|access_token|refresh_token|id_token|otp)\"\\s*:\\s*\")([^\"]*)(\")",
                    Pattern.CASE_INSENSITIVE),
            Pattern.compile(
                    "(?i)(\"(?:ghana_card_number|card_number|national_id)\"\\s*:\\s*\")([^\"]*)(\")",
                    Pattern.CASE_INSENSITIVE),
            // Plain text patterns (e.g. logged in a string message)
            Pattern.compile(
                    "(?i)(password\\s*[=:]\\s*)([^\\s,}&]+)",
                    Pattern.CASE_INSENSITIVE),
            Pattern.compile(
                    "(?i)(token\\s*[=:]\\s*)([^\\s,}&]+)",
                    Pattern.CASE_INSENSITIVE)
    );

    @Override
    public String convert(ILoggingEvent event) {
        String message = event.getFormattedMessage();
        if (message == null) return "";

        for (Pattern pattern : SENSITIVE_PATTERNS) {
            var matcher = pattern.matcher(message);
            if (pattern.pattern().contains("(\"")) {
                // JSON pattern: preserve key and surrounding quotes
                message = matcher.replaceAll("$1" + REDACTED + "$3");
            } else {
                // Plain text pattern: preserve key, redact value
                message = matcher.replaceAll("$1" + REDACTED);
            }
        }
        return message;
    }
}
