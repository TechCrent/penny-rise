package com.stash.admin.api.dto;

import java.time.Instant;

public record AdminTransactionSummary(
        String  reference,
        String  type,
        long    amountPesewas,
        String  status,
        Instant occurredAt
) {}
