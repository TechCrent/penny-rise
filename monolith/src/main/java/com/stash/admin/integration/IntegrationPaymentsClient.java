package com.stash.admin.integration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Calls Payments Service's real endpoints for admin transaction history,
 * transaction detail, ledger account balances, and account closure — all
 * gated by the shared {@code X-Internal-Service-Token} convention (see
 * docs/hands-on-testing-findings.md Finding 8 and
 * docs/gap-analysis-vendor-dependent-followup.md for the history behind
 * why these were stubs).
 */
@Component
public class IntegrationPaymentsClient {

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

    /**
     * Returns the {@code limit} most recent transactions for a user, calling
     * the same {@code GET /api/v1/transactions} endpoint as
     * {@link #getUnifiedTransactionHistory}, just without cursor/type/date
     * filters. Used by the admin user-detail page.
     */
    public List<TransactionRecord> getRecentTransactionsForUser(UUID userId, int limit) {
        var uriBuilder = UriComponentsBuilder.fromPath("/api/v1/transactions")
                .queryParam("user_id", userId)
                .queryParam("limit", limit);

        PaymentsUnifiedHistoryResponse response = paymentsClient.get()
                .uri(uriBuilder.build().toUriString())
                .retrieve()
                .body(PaymentsUnifiedHistoryResponse.class);

        if (response == null) {
            return List.of();
        }

        return response.transactions().stream()
                .map(r -> new TransactionRecord(
                        r.reference(), r.transactionType(), r.netAmount(), r.status(), r.createdAt()))
                .toList();
    }

    /**
     * Looks up a single transaction by its UUID primary key, calling
     * Payments Service's {@code GET /api/v1/transactions/by-id/{id}}
     * endpoint. Returns empty if the transaction doesn't exist.
     */
    public Optional<TransactionDetail> getTransactionById(UUID transactionId) {
        try {
            PaymentsTransactionDetailResponse response = paymentsClient.get()
                    .uri("/api/v1/transactions/by-id/{id}", transactionId)
                    .retrieve()
                    .body(PaymentsTransactionDetailResponse.class);

            if (response == null) {
                return Optional.empty();
            }

            return Optional.of(new TransactionDetail(
                    transactionId, response.initiatingUserId(), response.reference(),
                    response.transactionType(), response.netAmountPesewas(), response.status()));
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        }
    }

    /**
     * Closes a ledger account in Payments Service (v0.5-019 deletion saga,
     * step 3/4), calling {@code POST /internal/v1/ledger/accounts/{id}/close}.
     * Idempotent server-side (closing an already-CLOSED account is a no-op);
     * rejects with a non-2xx (propagated as a {@code RuntimeException}) if
     * the account's balance isn't zero.
     */
    public void closeLedgerAccount(UUID ledgerAccountId) {
        paymentsClient.post()
                .uri("/internal/v1/ledger/accounts/{id}/close", ledgerAccountId)
                .retrieve()
                .toBodilessEntity();
    }

    /**
     * Returns a cursor-paginated unified transaction history for the given
     * user, calling Payments Service's real {@code GET /api/v1/transactions}
     * endpoint (see docs/hands-on-testing-findings.md Finding 8).
     *
     * @param scope "vault" / "wallet" / {@code null} for unscoped — narrows
     *              to a single account's activity, used by Home's
     *              Savings/Wallet states.
     */
    public UnifiedTransactionPage getUnifiedTransactionHistory(UUID userId, String transactionType,
                                                                java.time.Instant fromDate,
                                                                java.time.Instant toDate,
                                                                String cursor, int limit,
                                                                String scope) {
        var uriBuilder = UriComponentsBuilder.fromPath("/api/v1/transactions")
                .queryParam("user_id", userId)
                .queryParam("limit", limit);
        if (transactionType != null) uriBuilder.queryParam("type", transactionType);
        if (fromDate != null) uriBuilder.queryParam("from_date", fromDate);
        if (toDate != null) uriBuilder.queryParam("to_date", toDate);
        if (cursor != null) uriBuilder.queryParam("cursor", cursor);
        if (scope != null) uriBuilder.queryParam("scope", scope);

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

    private record PaymentsTransactionDetailResponse(
            String reference,
            @com.fasterxml.jackson.annotation.JsonProperty("transaction_type") String transactionType,
            String status,
            @com.fasterxml.jackson.annotation.JsonProperty("net_amount_pesewas") long netAmountPesewas,
            @com.fasterxml.jackson.annotation.JsonProperty("initiating_user_id") UUID initiatingUserId
    ) {}

    /**
     * Returns a ledger account's current balance in pesewas (v0.5-034),
     * calling Payments Service's {@code GET /api/v1/accounts/{id}/balance}
     * endpoint — internal callers may query any account regardless of owner.
     */
    public long getLedgerAccountBalance(UUID ledgerAccountId) {
        PaymentsBalanceResponse response = paymentsClient.get()
                .uri("/api/v1/accounts/{id}/balance", ledgerAccountId)
                .retrieve()
                .body(PaymentsBalanceResponse.class);

        return response != null ? response.balancePesewas() : 0L;
    }

    private record PaymentsBalanceResponse(
            @com.fasterxml.jackson.annotation.JsonProperty("balance_pesewas") long balancePesewas
    ) {}
}
