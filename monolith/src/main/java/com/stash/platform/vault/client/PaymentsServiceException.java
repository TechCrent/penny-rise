package com.stash.platform.vault.client;

public class PaymentsServiceException extends RuntimeException {
    private final int httpStatus;

    public PaymentsServiceException(String message, int httpStatus, Throwable cause) {
        super(message, cause);
        this.httpStatus = httpStatus;
    }

    public int getHttpStatus() { return httpStatus; }
}
