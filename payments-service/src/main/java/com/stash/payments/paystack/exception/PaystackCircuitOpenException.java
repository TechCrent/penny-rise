package com.stash.payments.paystack.exception;

/** Thrown when the circuit breaker is open — fail fast, no network call. */
public class PaystackCircuitOpenException extends RuntimeException {
    public PaystackCircuitOpenException() {
        super("Paystack circuit breaker is open. Refusing outbound call to protect the platform.");
    }
}
