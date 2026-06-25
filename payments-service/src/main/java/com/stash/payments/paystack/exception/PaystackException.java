package com.stash.payments.paystack.exception;

public class PaystackException extends RuntimeException {
    private final int httpStatus;

    public PaystackException(String message, int httpStatus) {
        super(message);
        this.httpStatus = httpStatus;
    }
    public int getHttpStatus() { return httpStatus; }
}
