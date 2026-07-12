package com.stash.payments.transaction.service;

import com.stash.payments.ledger.service.LedgerService;
import com.stash.payments.transaction.api.dto.UnifiedTransactionHistoryResponse;
import com.stash.payments.transaction.repository.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.nio.charset.StandardCharsets;

/**
 * Real implementation behind the unified transaction history endpoint —
 * see docs/hands-on-testing-findings.md Finding 8. Previously
 * {@code IntegrationPaymentsClient} on the monolith side called nothing
 * and always returned an empty page.
 *
 * <p>Narrative and business-reference enrichment (from
 * {@code ledger.ledger_transactions}) is batch-fetched via
 * {@link LedgerService#getBusinessReferencesForTransactions} rather than
 * joined directly in {@code findHistoryForUser} — direct access to
 * {@code LedgerTransactionRepository} is restricted to {@code LedgerService}
 * by {@code LedgerWriteArchitectureTest}.
 */
@Service
public class TransactionHistoryQueryService {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 50;

    private final TransactionRepository transactionRepository;
    private final LedgerService ledgerService;

    public TransactionHistoryQueryService(TransactionRepository transactionRepository,
                                           LedgerService ledgerService) {
        this.transactionRepository = transactionRepository;
        this.ledgerService = ledgerService;
    }

    @Transactional(readOnly = true)
    public UnifiedTransactionHistoryResponse getHistory(UUID userId, String type,
                                                          Instant fromDate, Instant toDate,
                                                          String cursor, Integer requestedLimit,
                                                          String scope) {
        int limit = clampLimit(requestedLimit);
        Instant cursorCreatedAt = decodeCursor(cursor);

        List<Object[]> rows = transactionRepository.findHistoryForUser(
                userId, type, fromDate, toDate, cursorCreatedAt, scope, limit + 1);

        boolean hasMore = rows.size() > limit;
        List<Object[]> page = hasMore ? rows.subList(0, limit) : rows;

        Map<UUID, Object[]> businessReferencesByLedgerTxnId = fetchBusinessReferences(page);

        List<UnifiedTransactionHistoryResponse.Row> items = page.stream()
                .map(row -> toRow(row, businessReferencesByLedgerTxnId))
                .toList();

        String nextCursor = hasMore && !page.isEmpty()
                ? encodeCursor((Instant) page.get(page.size() - 1)[10])
                : null;

        return new UnifiedTransactionHistoryResponse(items, nextCursor, hasMore);
    }

    private Map<UUID, Object[]> fetchBusinessReferences(List<Object[]> page) {
        List<UUID> ledgerTxnIds = page.stream()
                .map(row -> (UUID) row[9])
                .filter(java.util.Objects::nonNull)
                .toList();

        Map<UUID, Object[]> byLedgerTxnId = new HashMap<>();
        for (Object[] ref : ledgerService.getBusinessReferencesForTransactions(ledgerTxnIds)) {
            byLedgerTxnId.put((UUID) ref[0], ref);
        }
        return byLedgerTxnId;
    }

    private UnifiedTransactionHistoryResponse.Row toRow(Object[] row,
                                                          Map<UUID, Object[]> businessReferencesByLedgerTxnId) {
        Object[] ref = businessReferencesByLedgerTxnId.get((UUID) row[9]);

        return new UnifiedTransactionHistoryResponse.Row(
                (String) row[1],
                (String) row[2],
                (UUID) row[3],
                (UUID) row[4],
                ((Number) row[5]).longValue(),
                ((Number) row[6]).longValue(),
                ((Number) row[7]).longValue(),
                (String) row[8],
                ref != null ? (String) ref[1] : null,
                ref != null ? (UUID) ref[2] : null,
                ref != null ? (String) ref[3] : null,
                (Instant) row[10]
        );
    }

    private int clampLimit(Integer requested) {
        if (requested == null) return DEFAULT_LIMIT;
        return Math.min(Math.max(requested, 1), MAX_LIMIT);
    }

    private Instant decodeCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) return null;
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            return Instant.parse(decoded);
        } catch (Exception e) {
            return null;
        }
    }

    private String encodeCursor(Instant createdAt) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(createdAt.toString().getBytes(StandardCharsets.UTF_8));
    }
}
