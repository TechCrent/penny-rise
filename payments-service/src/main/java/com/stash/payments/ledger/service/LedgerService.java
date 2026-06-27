package com.stash.payments.ledger.service;

import com.stash.payments.ledger.domain.EntryDirection;
import com.stash.payments.ledger.domain.EntryRequest;
import com.stash.payments.ledger.domain.LedgerEntryEntity;
import com.stash.payments.ledger.domain.LedgerTransactionEntity;
import com.stash.payments.ledger.event.LedgerTransactionPostedEvent;
import com.stash.payments.ledger.exception.LedgerImbalanceException;
import com.stash.payments.ledger.repository.LedgerEntryRepository;
import com.stash.payments.ledger.repository.LedgerTransactionRepository;
import com.stash.payments.outbox.service.OutboxPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Single entry point for all ledger writes on the Payments Service.
 *
 * <p><strong>No other code may write directly to {@code ledger_transactions}
 * or {@code ledger_entries}.</strong> Both repositories are package-private;
 * this service is the only class in the package that can import them.
 * The architecture test in {@code LedgerWriteArchitectureTest} verifies this
 * at build time.
 *
 * <p><strong>Double-entry invariant:</strong> SUM(DEBIT entries) must equal
 * SUM(CREDIT entries). Validated before any write. If violated,
 * {@link LedgerImbalanceException} is thrown and no DB state is changed.
 *
 * <p><strong>Atomicity:</strong> the entire write — transaction row, all
 * entry rows, outbox event — executes in one {@code @Transactional} context.
 * If any part fails, everything rolls back. There is no partial state.
 */
@Service
public class LedgerService {

    private static final Logger log = LoggerFactory.getLogger(LedgerService.class);

    private final LedgerTransactionRepository txnRepo;
    private final LedgerEntryRepository       entryRepo;
    private final OutboxPublisher             outboxPublisher;
    private final Clock                       clock;

    public LedgerService(LedgerTransactionRepository txnRepo,
                         LedgerEntryRepository entryRepo,
                         OutboxPublisher outboxPublisher,
                         Clock clock) {
        this.txnRepo         = txnRepo;
        this.entryRepo       = entryRepo;
        this.outboxPublisher = outboxPublisher;
        this.clock           = clock;
    }

    /**
     * Fetches all ledger entries for a transaction, with each entry's account type.
     * Exposed here (not on the repository) so callers outside this package can read
     * transaction entries without violating the repository access constraint.
     */
    @Transactional(readOnly = true)
    public List<Object[]> getEntriesForTransaction(UUID ledgerTransactionId) {
        return entryRepo.findEntriesWithAccountType(ledgerTransactionId);
    }

    /**
     * Posts a double-entry ledger transaction atomically.
     *
     * <p>Steps:
     * <ol>
     *   <li>Validate: at least two entries; SUM(DEBIT) == SUM(CREDIT).</li>
     *   <li>Insert {@code ledger_transactions} row with status POSTED.</li>
     *   <li>Insert all {@code ledger_entries} rows.</li>
     *   <li>Publish {@code LedgerTransactionPostedEvent} via outbox
     *       (same transaction).</li>
     * </ol>
     *
     * @throws LedgerImbalanceException  if entries do not balance — thrown
     *         before any write; no DB state changed
     * @throws IllegalArgumentException  if fewer than two entries supplied
     */
    @Transactional
    public LedgerWriteResult writeTransaction(LedgerWriteCommand command) {
        List<EntryRequest> entries = command.entries();
        Instant now = Instant.now(clock);

        // ── Step 1: Validate double-entry invariant ───────────────────────
        validateBalance(entries);

        long totalDebits = entries.stream()
                .filter(e -> e.direction() == EntryDirection.DEBIT)
                .mapToLong(EntryRequest::amount)
                .sum();

        // ── Step 2: Insert ledger_transactions ────────────────────────────
        LedgerTransactionEntity txn = new LedgerTransactionEntity(
                command.transactionType(),
                command.businessReferenceId(),
                command.businessReferenceType(),
                command.transactionReference(),
                totalDebits,
                command.correlationId(),
                command.narrative(),
                now
        );
        LedgerTransactionEntity savedTxn = txnRepo.save(txn);

        // ── Step 3: Insert ledger_entries ─────────────────────────────────
        for (EntryRequest entry : entries) {
            LedgerEntryEntity entryEntity = new LedgerEntryEntity(
                    savedTxn.getId(),
                    entry.accountId(),
                    entry.direction(),
                    entry.amount(),
                    entry.narrative(),
                    now
            );
            entryRepo.save(entryEntity);
        }

        // ── Step 4: Publish outbox event (same transaction) ───────────────
        LedgerTransactionPostedEvent event = new LedgerTransactionPostedEvent(
                savedTxn.getId(),
                command.transactionType(),
                command.transactionReference(),
                totalDebits,
                command.correlationId()
        );
        outboxPublisher.publish(event, command.correlationId());

        log.info("Ledger transaction posted: ref={} type={} amount={}p entries={} correlation={}",
                command.transactionReference(),
                command.transactionType(),
                totalDebits,
                entries.size(),
                command.correlationId());

        return new LedgerWriteResult(
                savedTxn.getId(),
                command.transactionReference(),
                totalDebits
        );
    }

    // ── Private helpers ───────────────────────────────────────────────────

    private void validateBalance(List<EntryRequest> entries) {
        long totalDebits = entries.stream()
                .filter(e -> e.direction() == EntryDirection.DEBIT)
                .mapToLong(EntryRequest::amount)
                .sum();

        long totalCredits = entries.stream()
                .filter(e -> e.direction() == EntryDirection.CREDIT)
                .mapToLong(EntryRequest::amount)
                .sum();

        if (totalDebits != totalCredits) {
            log.error("Ledger imbalance detected BEFORE write: debits={}p credits={}p ref={}",
                    totalDebits, totalCredits, "pre-write");
            throw new LedgerImbalanceException(totalDebits, totalCredits);
        }
    }
}
