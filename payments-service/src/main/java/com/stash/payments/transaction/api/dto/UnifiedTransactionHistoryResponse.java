package com.stash.payments.transaction.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Response for {@code GET /api/v1/transactions} — the real implementation
 * behind what was previously {@code IntegrationPaymentsClient}'s permanent
 * stub (see docs/hands-on-testing-findings.md Finding 8). Field names match
 * monolith's {@code PaymentsTransactionRow}/{@code UnifiedTransactionPage}
 * contract exactly.
 */
public record UnifiedTransactionHistoryResponse(
        List<Row> transactions,
        @JsonProperty("next_cursor") String nextCursor,
        @JsonProperty("has_more") boolean hasMore
) {
    public record Row(
            String reference,
            @JsonProperty("transaction_type") String transactionType,
            @JsonProperty("initiating_user_id") UUID initiatingUserId,
            @JsonProperty("counterparty_user_id") UUID counterpartyUserId,
            @JsonProperty("gross_amount") long grossAmount,
            @JsonProperty("fee_amount") long feeAmount,
            @JsonProperty("net_amount") long netAmount,
            String status,
            @JsonProperty("business_reference_type") String businessReferenceType,
            @JsonProperty("business_reference_id") UUID businessReferenceId,
            String narrative,
            @JsonProperty("created_at") Instant createdAt
    ) {}
}
