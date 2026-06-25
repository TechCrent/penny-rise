package com.stash.payments.paystack.dto;

public record TransferInitiateRequest(
        String source,              // "balance"
        long amount,
        String recipient,           // Paystack recipient code
        String reason,
        String reference,           // our idempotency reference
        String currency             // "GHS"
) {}
