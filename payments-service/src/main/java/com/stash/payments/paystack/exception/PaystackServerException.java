package com.stash.payments.paystack.exception;

/** 5xx from Paystack — transient, eligible for retry on idempotent calls. */
public class PaystackServerException extends PaystackException {
    public PaystackServerException(String message, int httpStatus) {
        super(message, httpStatus);
    }
}
