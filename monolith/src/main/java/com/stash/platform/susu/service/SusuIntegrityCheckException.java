package com.stash.platform.susu.service;

public class SusuIntegrityCheckException extends RuntimeException {

    private final long expectedPesewas;
    private final long actualPesewas;

    public SusuIntegrityCheckException(String message) {
        super(message);
        this.expectedPesewas = -1;
        this.actualPesewas   = -1;
    }

    public SusuIntegrityCheckException(String message, long expected, long actual) {
        super(message);
        this.expectedPesewas = expected;
        this.actualPesewas   = actual;
    }

    public long getExpectedPesewas() { return expectedPesewas; }
    public long getActualPesewas()   { return actualPesewas; }
}
