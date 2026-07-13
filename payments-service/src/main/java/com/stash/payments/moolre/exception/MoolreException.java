package com.stash.payments.moolre.exception;

public class MoolreException extends RuntimeException {
    private final int httpStatus;

    public MoolreException(String message, int httpStatus) {
        super(message);
        this.httpStatus = httpStatus;
    }

    public int getHttpStatus() {
        return httpStatus;
    }
}
