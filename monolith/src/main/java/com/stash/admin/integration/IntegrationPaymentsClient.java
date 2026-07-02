package com.stash.admin.integration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * BLOCKING DEPENDENCY — stub implementation.
 *
 * <p>Will delegate to the payments micro-service admin API once that endpoint
 * is exposed. Until then, returns an empty list so the admin user-detail
 * endpoint compiles and starts without errors.
 */
@Component
public class IntegrationPaymentsClient {

    private static final Logger log = LoggerFactory.getLogger(IntegrationPaymentsClient.class);

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
     * Returns a cursor-paginated unified transaction history for the given user (v0.5-020).
     *
     * <p>STUB — requires a new Payments Service endpoint:
     * GET /api/v1/transactions?user_id=&amp;...
     * querying WHERE initiating_user_id = :user_id OR counterparty_user_id = :user_id,
     * with a supporting index on (counterparty_user_id, created_at DESC).
     * This endpoint serves all four historical call sites that need transaction lists by user
     * (v0.5-005, v0.5-007, v0.5-019, v0.5-020) — strongly recommended to build one endpoint
     * well rather than four ad-hoc methods.
     */
    public UnifiedTransactionPage getUnifiedTransactionHistory(UUID userId, String transactionType,
                                                                java.time.Instant fromDate,
                                                                java.time.Instant toDate,
                                                                String cursor, int limit) {
        log.debug("IntegrationPaymentsClient is a stub — returning empty transaction history for user {}", userId);
        return new UnifiedTransactionPage(List.of(), null, false);
    }
}
