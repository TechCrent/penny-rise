package com.stash.payments.paystack.exception;

/** 4xx from Paystack — client error, not retried. */
public class PaystackClientException extends PaystackException {
    public PaystackClientException(String message, int httpStatus) {
        super(message, httpStatus);
    }
}
