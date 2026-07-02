package com.stash.admin.integration;

import java.time.Instant;
import java.util.UUID;

/**
 * A single row returned by Payments Service's unified transaction endpoint
 * (v0.5-020). The Payments side is responsible for pagination and filtering;
 * the monolith side enriches these rows with account_name, direction, and
 * counterparty_name.
 *
 * <p>business_reference_type / business_reference_id are used by
 * TransactionHistoryEnricher to resolve vault/susu group names — these
 * fields exist in ledger_transactions.business_reference_type/id (Schema
 * doc §6.2) and are the bridge between the Payments ledger and the
 * business objects (vaults, susu groups) that triggered each transaction.
 */
public record PaymentsTransactionRow(
        String reference,
        String transactionType,
        UUID initiatingUserId,
        UUID counterpartyUserId,
        long grossAmount,
        long feeAmount,
        long netAmount,
        String status,
        String businessReferenceType,
        UUID businessReferenceId,
        String narrative,
        Instant createdAt
) {}
