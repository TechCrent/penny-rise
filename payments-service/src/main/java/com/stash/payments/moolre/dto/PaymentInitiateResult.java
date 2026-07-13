package com.stash.payments.moolre.dto;

/**
 * Result of {@code /open/transact/payment}. {@code data} is typically a session id
 * (success) or a hint string (e.g. OTP required — code {@code TP14}).
 */
public record PaymentInitiateResult(
        String code,
        String message,
        String data,
        boolean success
) {
    public static PaymentInitiateResult from(MoolreEnvelope envelope) {
        return new PaymentInitiateResult(
                envelope.code(),
                envelope.messageAsText(),
                envelope.dataAsText(),
                envelope.isSuccess());
    }

    /** Alias for {@link #data()} when the payload is a USSD/session id. */
    public String sessionId() {
        return data;
    }
}
