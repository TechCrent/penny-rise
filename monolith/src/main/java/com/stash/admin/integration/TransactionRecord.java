package com.stash.admin.integration;

import java.time.Instant;

/** Single transaction event from the payments service admin API. */
public record TransactionRecord(
        String  reference,
        String  type,
        long    amountPesewas,
        String  status,
        Instant occurredAt
) {}
