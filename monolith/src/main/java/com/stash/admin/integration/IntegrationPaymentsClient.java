package com.stash.admin.integration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * BLOCKING DEPENDENCY — mostly a stub implementation.
 *
 * <p>Will delegate to the payments micro-service admin API once those
 * endpoints are exposed. Until then, most methods return empty results so
 * callers compile and start without errors.
 *
 * <p>{@link #getUnifiedTransactionHistory} is the one exception — see
 * docs/hands-on-testing-findings.md Finding 8: this was blocking the real,
 * working transaction-history endpoint and this branch's new statement
 * export feature from ever showing real data, so it's wired to the real
 * Payments Service endpoint. The other methods remain documented stubs;
 * see docs/gap-analysis-vendor-dependent-followup.md for what's needed to
 * finish them.
 */
@Component
public class IntegrationPaymentsClient {

    private static final Logger log = LoggerFactory.getLogger(IntegrationPaymentsClient.class);

    private final RestClient paymentsClient;

    public IntegrationPaymentsClient(
            RestClient.Builder builder,
            @Value("${stash.payments.internal-base-url}") String baseUrl,
            @Value("${stash.internal.service-token}") String serviceToken) {
        this.paymentsClient = builder
                .baseUrl(baseUrl)
                .defaultHeader("X-Internal-Service-Token", serviceToken)
                .build();
    }

    public List<TransactionRecord> getRecentTransactionsForUser(UUID userId, int limit) {
        log.debug("IntegrationPaymentsClient is a stub — returning empty transaction list for user {}", userId);
        return List.of();
    }

    public Optional<TransactionDetail> getTransactionById(UUID transactionId) {
        log.debug("IntegrationPaymentsClient is a stub — returning empty for transaction {}", transactionId);
        return Optional.empty();
    }

    /**
     * Closes a ledger account in Payments Service (v0.5-019 deletion saga, step 3/4).
     *
     * <p>STUB — requires a new Payments Service endpoint (proposed:
     * POST /api/v1/accounts/{id}/close) that does not yet exist in the documented
     * public API surface. The endpoint MUST be idempotent (closing an already-CLOSED
     * account is a no-op, not an error) and MUST reject if balance != 0.
     */
    public void closeLedgerAccount(UUID ledgerAccountId) {
        log.debug("IntegrationPaymentsClient is a stub — skipping closeLedgerAccount for {}", ledgerAccountId);
    }

    /**
     * Returns a cursor-paginated unified transaction history for the given
     * user, calling Payments Service's real {@code GET /api/v1/transactions}
     * endpoint (see docs/hands-on-testing-findings.md Finding 8).
     */
    public UnifiedTransactionPage getUnifiedTransactionHistory(UUID userId, String transactionType,
                                                                java.time.Instant fromDate,
                                                                java.time.Instant toDate,
                                                                String cursor, int limit) {
        var uriBuilder = UriComponentsBuilder.fromPath("/api/v1/transactions")
                .queryParam("user_id", userId)
                .queryParam("limit", limit);
        if (transactionType != null) uriBuilder.queryParam("type", transactionType);
        if (fromDate != null) uriBuilder.queryParam("from_date", fromDate);
        if (toDate != null) uriBuilder.queryParam("to_date", toDate);
        if (cursor != null) uriBuilder.queryParam("cursor", cursor);

        PaymentsUnifiedHistoryResponse response = paymentsClient.get()
                .uri(uriBuilder.build().toUriString())
                .retrieve()
                .body(PaymentsUnifiedHistoryResponse.class);

        if (response == null) {
            return new UnifiedTransactionPage(List.of(), null, false);
        }

        List<PaymentsTransactionRow> rows = response.transactions().stream()
                .map(r -> new PaymentsTransactionRow(
                        r.reference(), r.transactionType(), r.initiatingUserId(),
                        r.counterpartyUserId(), r.grossAmount(), r.feeAmount(), r.netAmount(),
                        r.status(), r.businessReferenceType(), r.businessReferenceId(),
                        r.narrative(), r.createdAt()))
                .toList();

        return new UnifiedTransactionPage(rows, response.nextCursor(), response.hasMore());
    }

    private record PaymentsUnifiedHistoryResponse(
            List<Row> transactions,
            @com.fasterxml.jackson.annotation.JsonProperty("next_cursor") String nextCursor,
            @com.fasterxml.jackson.annotation.JsonProperty("has_more") boolean hasMore
    ) {
        private record Row(
                String reference,
                @com.fasterxml.jackson.annotation.JsonProperty("transaction_type") String transactionType,
                @com.fasterxml.jackson.annotation.JsonProperty("initiating_user_id") UUID initiatingUserId,
                @com.fasterxml.jackson.annotation.JsonProperty("counterparty_user_id") UUID counterpartyUserId,
                @com.fasterxml.jackson.annotation.JsonProperty("gross_amount") long grossAmount,
                @com.fasterxml.jackson.annotation.JsonProperty("fee_amount") long feeAmount,
                @com.fasterxml.jackson.annotation.JsonProperty("net_amount") long netAmount,
                String status,
                @com.fasterxml.jackson.annotation.JsonProperty("business_reference_type") String businessReferenceType,
                @com.fasterxml.jackson.annotation.JsonProperty("business_reference_id") UUID businessReferenceId,
                String narrative,
                @com.fasterxml.jackson.annotation.JsonProperty("created_at") java.time.Instant createdAt
        ) {}
    }

    /**
     * Returns a ledger account's current balance in pesewas (v0.5-034).
     *
     * <p>STUB — matches {@link #toVaultSummary} in AdminUserService, which
     * already hardcodes vault balances to 0L for the exact same reason:
     * no real balance-lookup endpoint exists in this client at all yet.
     * Requires a new Payments Service endpoint (proposed:
     * GET /api/v1/accounts/{id}/balance) that does not yet exist in the
     * documented public API surface.
     */
    public long getLedgerAccountBalance(UUID ledgerAccountId) {
        log.debug("IntegrationPaymentsClient is a stub — returning 0 balance for ledger account {}",
                ledgerAccountId);
        return 0L;
    }
}
