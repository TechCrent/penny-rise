package com.stash.admin.integration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
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
}
