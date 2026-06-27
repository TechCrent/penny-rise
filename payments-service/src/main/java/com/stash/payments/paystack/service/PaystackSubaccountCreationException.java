package com.stash.payments.paystack.service;

/** Thrown when Paystack returns status=false on subaccount creation. */
public class PaystackSubaccountCreationException extends RuntimeException {
    public PaystackSubaccountCreationException(String message) {
        super(message);
    }
}
