package com.stash.payments.transaction.service;

import com.stash.payments.transaction.api.dto.UnifiedTransactionHistoryResponse;
import com.stash.payments.transaction.repository.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.nio.charset.StandardCharsets;

/**
 * Real implementation behind the unified transaction history endpoint —
 * see docs/hands-on-testing-findings.md Finding 8. Previously
 * {@code IntegrationPaymentsClient} on the monolith side called nothing
 * and always returned an empty page.
 *
 * <p>Narrative and business-reference enrichment (from
 * {@code ledger.ledger_transactions}) is intentionally not joined here —
 * {@code LedgerTransactionRepository} access is restricted to
 * {@code LedgerService} by {@code LedgerWriteArchitectureTest}. Rows are
 * returned with those fields {@code null}; wiring that enrichment through
 * {@code LedgerService} is noted as a follow-up, not required for the
 * transaction list itself to be real.
 */
@Service
public class TransactionHistoryQueryService {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 50;

    private final TransactionRepository transactionRepository;

    public TransactionHistoryQueryService(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    @Transactional(readOnly = true)
    public UnifiedTransactionHistoryResponse getHistory(UUID userId, String type,
                                                          Instant fromDate, Instant toDate,
                                                          String cursor, Integer requestedLimit) {
        int limit = clampLimit(requestedLimit);
        Instant cursorCreatedAt = decodeCursor(cursor);

        List<Object[]> rows = transactionRepository.findHistoryForUser(
                userId, type, fromDate, toDate, cursorCreatedAt, limit + 1);

        boolean hasMore = rows.size() > limit;
        List<Object[]> page = hasMore ? rows.subList(0, limit) : rows;

        List<UnifiedTransactionHistoryResponse.Row> items = page.stream()
                .map(this::toRow)
                .toList();

        String nextCursor = hasMore && !page.isEmpty()
                ? encodeCursor((Instant) page.get(page.size() - 1)[10])
                : null;

        return new UnifiedTransactionHistoryResponse(items, nextCursor, hasMore);
    }

    private UnifiedTransactionHistoryResponse.Row toRow(Object[] row) {
        return new UnifiedTransactionHistoryResponse.Row(
                (String) row[1],
                (String) row[2],
                (UUID) row[3],
                (UUID) row[4],
                ((Number) row[5]).longValue(),
                ((Number) row[6]).longValue(),
                ((Number) row[7]).longValue(),
                (String) row[8],
                null,
                null,
                null,
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
