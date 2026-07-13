package com.stash.payments.moolre.exception;

/** 5xx from Moolre — transient, eligible for retry on idempotent calls. */
public class MoolreServerException extends MoolreException {
    public MoolreServerException(String message, int httpStatus) {
        super(message, httpStatus);
    }
}
