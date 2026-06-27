package com.stash.payments.transaction.service;

import com.stash.payments.ledger.domain.EntryDirection;
import com.stash.payments.ledger.domain.EntryRequest;
import com.stash.payments.ledger.domain.LedgerAccountEntity;
import com.stash.payments.ledger.exception.InsufficientBalanceException;
import com.stash.payments.ledger.repository.LedgerAccountRepository;
import com.stash.payments.ledger.service.BalanceService;
import com.stash.payments.ledger.service.LedgerService;
import com.stash.payments.ledger.service.LedgerWriteCommand;
import com.stash.payments.ledger.service.LedgerWriteResult;
import com.stash.payments.ledger.service.TransactionReferenceGenerator;
import com.stash.payments.outbox.service.OutboxPublisher;
import com.stash.payments.transaction.api.dto.TransferRequest;
import com.stash.payments.transaction.api.dto.TransferResponse;
import com.stash.payments.transaction.domain.TransactionEntity;
import com.stash.payments.transaction.event.TransferPostedEvent;
import com.stash.payments.transaction.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Posts internal ledger transfers synchronously.
 *
 * <p>Internal transfers (wallet → vault, wallet → susu pot, wallet → wallet)
 * do not involve Paystack. They write the ledger entries and commit in one
 * transaction, returning {@code 201 POSTED} immediately.
 *
 * <p><strong>Double-spend prevention:</strong> a {@code SELECT FOR UPDATE}
 * lock is acquired on the source account row before the balance check.
 * Concurrent transfers from the same source block at the lock and
 * re-check balance after the first commits — only one succeeds if balance
 * is tight.
 *
 * <p><strong>Transaction record:</strong> unlike deposits and withdrawals,
 * internal transfers complete synchronously, so the {@code transaction.transactions}
 * row is created in {@code COMPLETED} status directly — there is no PENDING
 * intermediate state.
 */
@Service
public class TransferService {

    private static final Logger log = LoggerFactory.getLogger(TransferService.class);

    private final LedgerAccountRepository       ledgerAccountRepo;
    private final BalanceService                balanceService;
    private final LedgerService                 ledgerService;
    private final TransactionRepository         transactionRepo;
    private final OutboxPublisher               outboxPublisher;
    private final TransactionReferenceGenerator referenceGenerator;
    private final Clock                         clock;

    public TransferService(LedgerAccountRepository ledgerAccountRepo,
                           BalanceService balanceService,
                           LedgerService ledgerService,
                           TransactionRepository transactionRepo,
                           OutboxPublisher outboxPublisher,
                           TransactionReferenceGenerator referenceGenerator,
                           Clock clock) {
        this.ledgerAccountRepo  = ledgerAccountRepo;
        this.balanceService     = balanceService;
        this.ledgerService      = ledgerService;
        this.transactionRepo    = transactionRepo;
        this.outboxPublisher    = outboxPublisher;
        this.referenceGenerator = referenceGenerator;
        this.clock              = clock;
    }

    /**
     * Executes an internal ledger transfer atomically.
     *
     * @throws InsufficientBalanceException if source balance < amount
     * @throws ResponseStatusException      404 if either account not found;
     *                                      409 if either account not ACTIVE;
     *                                      422 for self-transfer
     */
    @Transactional
    public TransferResponse transfer(TransferRequest request, String idempotencyKey) {
        // ── Idempotency: already processed? ──────────────────────────────
        Optional<TransactionEntity> existing =
                transactionRepo.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            TransactionEntity txn = existing.get();
            log.info("Transfer idempotent hit: idemKey={} txnRef={}", idempotencyKey, txn.getReference());
            return TransferResponse.posted(txn.getReference(), txn.getLedgerTransactionId());
        }

        // ── Validate self-transfer ────────────────────────────────────────
        if (request.sourceAccountId().equals(request.destinationAccountId())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Source and destination accounts must be different.");
        }

        // ── Validate accounts ─────────────────────────────────────────────
        LedgerAccountEntity source = requireActive(request.sourceAccountId(), "source");
        LedgerAccountEntity dest   = requireActive(request.destinationAccountId(), "destination");

        // ── Balance check with SELECT FOR UPDATE lock ─────────────────────
        long available = balanceService.computeBalanceWithLock(request.sourceAccountId());
        if (available < request.amount()) {
            throw new InsufficientBalanceException(available, request.amount());
        }

        // ── Generate reference ────────────────────────────────────────────
        String txnReference = generateUniqueReference();

        // ── Post ledger entries ───────────────────────────────────────────
        LedgerWriteCommand command = new LedgerWriteCommand(
                request.transactionType(),
                txnReference,
                request.businessReferenceId(),
                request.businessReferenceType(),
                List.of(
                        new EntryRequest(request.sourceAccountId(),
                                EntryDirection.DEBIT, request.amount(), request.narrative()),
                        new EntryRequest(request.destinationAccountId(),
                                EntryDirection.CREDIT, request.amount(), request.narrative())
                ),
                request.correlationId(),
                request.narrative()
        );
        LedgerWriteResult result = ledgerService.writeTransaction(command);

        // ── Create COMPLETED transaction row ──────────────────────────────
        TransactionEntity txn = TransactionEntity.completedTransfer(
                txnReference,
                source.getOwnerId(),
                dest.getOwnerId(),
                request.amount(),
                request.transactionType(),
                result.ledgerTransactionId(),
                request.correlationId(),
                idempotencyKey,
                Instant.now(clock)
        );
        transactionRepo.save(txn);

        // ── Outbox event ──────────────────────────────────────────────────
        outboxPublisher.publish(
                new TransferPostedEvent(
                        txn.getId(),
                        result.ledgerTransactionId(),
                        request.sourceAccountId(),
                        request.destinationAccountId(),
                        request.amount(),
                        request.transactionType(),
                        txnReference,
                        request.correlationId()),
                request.correlationId()
        );

        log.info("Transfer posted: ref={} type={} amount={}p src={} dst={} correlation={}",
                txnReference, request.transactionType(), request.amount(),
                request.sourceAccountId(), request.destinationAccountId(),
                request.correlationId());

        return TransferResponse.posted(txnReference, result.ledgerTransactionId());
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private LedgerAccountEntity requireActive(UUID accountId, String role) {
        LedgerAccountEntity account = ledgerAccountRepo.findById(accountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Ledger account not found (" + role + "): " + accountId));
        if (!"ACTIVE".equals(account.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ledger account is not ACTIVE (" + role + "): " +
                    accountId + " (status: " + account.getStatus() + ")");
        }
        return account;
    }

    private String generateUniqueReference() {
        for (int attempt = 0; attempt < 5; attempt++) {
            String candidate = referenceGenerator.generate();
            if (transactionRepo.findByReference(candidate).isEmpty()) return candidate;
        }
        throw new IllegalStateException("Failed to generate unique reference after 5 attempts.");
    }
}
