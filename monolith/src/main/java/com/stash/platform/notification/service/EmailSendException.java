package com.stash.platform.notification.service;

/**
 * Thrown when email delivery fails after all retry attempts.
 */
public class EmailSendException extends RuntimeException {

    public EmailSendException(String message) {
        super(message);
    }

    public EmailSendException(String message, Throwable cause) {
        super(message, cause);
    }
}