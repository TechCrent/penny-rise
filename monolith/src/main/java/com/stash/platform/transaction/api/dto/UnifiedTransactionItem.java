package com.stash.platform.transaction.api.dto;

import java.time.Instant;

public record UnifiedTransactionItem(
        String transactionReference,
        String transactionType,
        String accountName,
        String direction,
        long amountPesewas,
        String amountCedis,
        String narrative,
        String counterpartyName,
        String status,
        Instant createdAt
) {}
