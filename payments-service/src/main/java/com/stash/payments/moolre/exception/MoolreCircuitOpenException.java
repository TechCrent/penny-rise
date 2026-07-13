package com.stash.payments.moolre.exception;

/** Thrown when the circuit breaker is open — fail fast, no network call. */
public class MoolreCircuitOpenException extends RuntimeException {
    public MoolreCircuitOpenException() {
        super("Moolre circuit breaker is open. Refusing outbound call to protect the platform.");
    }
}
