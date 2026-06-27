package com.stash.payments.ledger.service;

import com.stash.payments.ledger.api.StatementCursor;
import com.stash.payments.ledger.api.dto.StatementEntryDto;
import com.stash.payments.ledger.api.dto.StatementResponse;
import com.stash.payments.ledger.domain.LedgerAccountEntity;
import com.stash.payments.ledger.repository.LedgerAccountRepository;
import com.stash.payments.ledger.repository.LedgerEntryRepository;
import com.stash.payments.shared.security.CallerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Generates paginated account statements from ledger entries.
 *
 * <p><strong>Running balance algorithm:</strong>
 * <ol>
 *   <li>Compute the current total balance of the account.</li>
 *   <li>Compute the signed sum of all entries <em>newer than</em> the oldest entry on the page.</li>
 *   <li>anchorBalance = totalBalance − newerSignedSum  (= balance after the oldest entry).</li>
 *   <li>balanceBeforeOldest = anchorBalance − oldestEntry.signed.</li>
 *   <li>Walk oldest → newest, adding each entry's signed amount to get its post-entry balance.</li>
 * </ol>
 *
 * <p>Requires exactly two SQL queries per page regardless of page number.
 */
@Service
public class StatementService {

    private static final Logger log = LoggerFactory.getLogger(StatementService.class);
    static final int MAX_LIMIT     = 50;
    static final int DEFAULT_LIMIT = 20;

    private final LedgerAccountRepository ledgerAccountRepo;
    private final LedgerEntryRepository   entryRepo;
    private final BalanceService          balanceService;

    public StatementService(LedgerAccountRepository ledgerAccountRepo,
                            LedgerEntryRepository entryRepo,
                            BalanceService balanceService) {
        this.ledgerAccountRepo = ledgerAccountRepo;
        this.entryRepo         = entryRepo;
        this.balanceService    = balanceService;
    }

    @Transactional(readOnly = true)
    public StatementResponse getStatement(UUID accountId,
                                           String cursorEncoded,
                                           Integer limitParam,
                                           Instant fromDate,
                                           Instant toDate,
                                           CallerContext callerContext) {
        LedgerAccountEntity account = ledgerAccountRepo.findById(accountId)
                .orElseThrow(() -> notFound(accountId));

        if (!callerContext.isInternal()) {
            enforceOwnership(account, callerContext, accountId);
        }

        StatementCursor cursor = null;
        if (cursorEncoded != null && !cursorEncoded.isBlank()) {
            try {
                cursor = StatementCursor.decode(cursorEncoded);
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Invalid cursor: " + cursorEncoded);
            }
        }

        int limit     = (limitParam != null) ? Math.min(Math.max(limitParam, 1), MAX_LIMIT) : DEFAULT_LIMIT;
        int fetchSize = limit + 1;

        List<Object[]> rows = entryRepo.findStatementPage(
                accountId,
                fromDate,
                toDate,
                cursor != null ? cursor.createdAt() : null,
                cursor != null ? cursor.entryId()   : null,
                fetchSize);

        boolean hasMore = rows.size() > limit;
        if (hasMore) rows = rows.subList(0, limit);

        if (rows.isEmpty()) {
            return StatementResponse.of(List.of(), null);
        }

        // ── Running balance anchor ────────────────────────────────────────
        // rows is newest-first; oldest entry is the last row
        Object[] oldestRow = rows.get(rows.size() - 1);
        Instant  oldestTs  = toInstant(oldestRow[4]);

        long totalBalance    = balanceService.computeBalanceFast(accountId);
        long newerSignedSum  = entryRepo.sumSignedAmountsAfter(accountId, oldestTs);
        long anchorBalance   = totalBalance - newerSignedSum;  // balance AFTER oldest entry

        // Compute balance BEFORE the oldest entry so we can walk forward adding each entry
        long oldestAmt    = ((Number) oldestRow[2]).longValue();
        boolean oldestCredit = "CREDIT".equals(oldestRow[1]);
        long runningBalance = anchorBalance - (oldestCredit ? oldestAmt : -oldestAmt);

        // ── Walk oldest → newest to assign running balance ────────────────
        List<Object[]> chronological = new ArrayList<>(rows);
        Collections.reverse(chronological);

        List<StatementEntryDto> entries = new ArrayList<>(rows.size());
        for (Object[] row : chronological) {
            UUID    entryId      = (UUID)   row[0];
            String  direction    = (String) row[1];
            long    amount       = ((Number) row[2]).longValue();
            String  narrative    = (String) row[3];
            Instant createdAt    = toInstant(row[4]);
            String  txnReference = (String) row[5];
            String  txnType      = (String) row[6];

            if ("CREDIT".equals(direction)) {
                runningBalance += amount;
            } else {
                runningBalance -= amount;
            }

            entries.add(StatementEntryDto.of(
                    entryId, direction, amount, runningBalance,
                    txnReference, txnType, narrative, createdAt));
        }

        // Return newest-first
        Collections.reverse(entries);

        // ── Build next cursor from the oldest entry on the page ───────────
        String nextCursor = null;
        if (hasMore) {
            Object[] oldestVisible = rows.get(rows.size() - 1);
            nextCursor = new StatementCursor(toInstant(oldestVisible[4]),
                    (UUID) oldestVisible[0]).encode();
        }

        log.debug("Statement page: account={} entries={} hasMore={}",
                accountId, entries.size(), hasMore);

        return StatementResponse.of(entries, nextCursor);
    }

    private void enforceOwnership(LedgerAccountEntity account,
                                   CallerContext caller, UUID accountId) {
        String type = account.getAccountType();
        if ("SYSTEM".equals(account.getOwnerType())
                || "PAYSTACK_SETTLEMENT".equals(type)
                || "FEE_REVENUE".equals(type)) {
            throw notFound(accountId);
        }
        if ("USER_WALLET".equals(type)) {
            if (caller.userId() == null || !account.getOwnerId().equals(caller.userId())) {
                throw notFound(accountId);
            }
            return;
        }
        throw notFound(accountId);
    }

    private static Instant toInstant(Object col) {
        return ((Timestamp) col).toInstant();
    }

    private ResponseStatusException notFound(UUID accountId) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Account not found: " + accountId);
    }
}
