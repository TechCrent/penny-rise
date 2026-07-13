package com.stash.payments.moolre.exception;

/** 4xx from Moolre — client error, not retried. */
public class MoolreClientException extends MoolreException {
    public MoolreClientException(String message, int httpStatus) {
        super(message, httpStatus);
    }
}
