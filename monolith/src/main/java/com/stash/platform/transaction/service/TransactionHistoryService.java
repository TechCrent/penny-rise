package com.stash.platform.transaction.service;

import com.stash.admin.integration.IntegrationPaymentsClient;
import com.stash.admin.integration.PaymentsTransactionRow;
import com.stash.platform.transaction.api.dto.UnifiedTransactionHistoryResponse;
import com.stash.platform.transaction.api.dto.UnifiedTransactionItem;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class TransactionHistoryService {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT     = 50;

    private final IntegrationPaymentsClient paymentsClient;
    private final TransactionHistoryEnricher enricher;

    public TransactionHistoryService(IntegrationPaymentsClient paymentsClient,
                                      TransactionHistoryEnricher enricher) {
        this.paymentsClient = paymentsClient;
        this.enricher       = enricher;
    }

    public UnifiedTransactionHistoryResponse list(UUID userId, String transactionType,
                                                   Instant fromDate, Instant toDate,
                                                   String cursor, Integer requestedLimit,
                                                   String scope) {
        int limit = clampLimit(requestedLimit);

        var page = paymentsClient.getUnifiedTransactionHistory(
                userId, transactionType, fromDate, toDate, cursor, limit, scope);

        var items = page.transactions().stream()
                .map(row -> toItem(row, userId))
                .toList();

        return new UnifiedTransactionHistoryResponse(items, page.nextCursor(), page.hasMore());
    }

    private UnifiedTransactionItem toItem(PaymentsTransactionRow row, UUID userId) {
        return new UnifiedTransactionItem(
                row.reference(),
                row.transactionType(),
                enricher.resolveAccountName(row),
                enricher.resolveDirection(row, userId),
                row.netAmount(),
                formatCedis(row.netAmount()),
                row.narrative(),
                enricher.resolveCounterpartyName(row),
                row.status(),
                row.createdAt()
        );
    }

    private String formatCedis(long pesewas) {
        return String.format("%.2f", pesewas / 100.0);
    }

    private int clampLimit(Integer requested) {
        if (requested == null) return DEFAULT_LIMIT;
        return Math.min(Math.max(requested, 1), MAX_LIMIT);
    }
}
