package com.stash.platform.notification.service;

/**
 * Immutable SMS message to be dispatched by {@link SmsSender}.
 *
 * <p>The recipient phone number and body are considered PII and must
 * not appear in logs at INFO level or above.
 *
 * @param to   recipient in provider format (e.g. {@code 233501234567} for Moolre)
 * @param body SMS text body
 * @param ref  optional provider-side reference for delivery tracking; may be {@code null}
 */
public record SmsMessage(
        String to,
        String body,
        String ref
) {
    public SmsMessage(String to, String body) {
        this(to, body, null);
    }
}
