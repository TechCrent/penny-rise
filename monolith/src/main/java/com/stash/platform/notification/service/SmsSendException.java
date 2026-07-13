package com.stash.platform.notification.service;

/**
 * Thrown when SMS delivery fails.
 */
public class SmsSendException extends RuntimeException {

    public SmsSendException(String message) {
        super(message);
    }

    public SmsSendException(String message, Throwable cause) {
        super(message, cause);
    }
}
