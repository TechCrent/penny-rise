package com.stash.platform.notification.service;

/**
 * Immutable email message to be dispatched by {@link EmailSender}.
 *
 * <p>The recipient address and subject are considered PII and must
 * not appear in logs at INFO level or above.
 */
public record EmailMessage(
        String to,
        String subject,
        String htmlBody,
        String textBody
) {}