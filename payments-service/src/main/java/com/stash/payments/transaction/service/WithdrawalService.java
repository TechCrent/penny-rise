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
import com.stash.payments.moolre.amount.MoolreAmountConverter;
import com.stash.payments.moolre.channel.MoolreChannelMapper;
import com.stash.payments.moolre.client.MoolreClient;
import com.stash.payments.moolre.dto.TransferResult;
import com.stash.payments.moolre.exception.MoolreClientException;
import com.stash.payments.moolre.phone.MomoPhoneFormatter;
import com.stash.payments.outbox.service.OutboxPublisher;
import com.stash.payments.transaction.api.dto.WithdrawalInitiateRequest;
import com.stash.payments.transaction.api.dto.WithdrawalInitiateResponse;
import com.stash.payments.transaction.domain.TransactionEntity;
import com.stash.payments.transaction.event.WithdrawalCompletedEvent;
import com.stash.payments.transaction.event.WithdrawalFailedEvent;
import com.stash.payments.transaction.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Initiates a withdrawal via Moolre MoMo transfer.
 *
 * <p><strong>Steps:</strong>
 * <ol>
 *   <li>Validate account exists, is ACTIVE, and belongs to the user.</li>
 *   <li>Acquire a SELECT FOR UPDATE lock on the account row.</li>
 *   <li>Compute balance inside the lock.</li>
 *   <li>Reject with 422 if insufficient balance.</li>
 *   <li>Post ledger entries (DEBIT source, CREDIT MOOLRE_SETTLEMENT) — the
 *       reservation. This debits the balance immediately.</li>
 *   <li>Optionally validate the MoMo recipient name.</li>
 *   <li>Initiate the Moolre transfer.</li>
 *   <li>If Moolre reports synchronous success ({@code txstatus==1}), complete
 *       immediately; otherwise store the provider id and return 202 PENDING.</li>
 * </ol>
 *
 * <p><strong>On Moolre failure after ledger write:</strong> the ledger entries
 * are already committed (the reservation is in place). The transaction is
 * reversed via a compensating ledger write before returning an error.
 */
@Service
public class WithdrawalService {

    private static final Logger log = LoggerFactory.getLogger(WithdrawalService.class);

    private final LedgerAccountRepository       ledgerAccountRepo;
    private final BalanceService                balanceService;
    private final LedgerService                 ledgerService;
    private final TransactionRepository         transactionRepo;
    private final MoolreClient                  moolreClient;
    private final OutboxPublisher               outboxPublisher;
    private final TransactionReferenceGenerator referenceGenerator;
    private final Clock                         clock;
    private final UUID                          moolreSettlementAccountId;

    public WithdrawalService(LedgerAccountRepository ledgerAccountRepo,
                             BalanceService balanceService,
                             LedgerService ledgerService,
                             TransactionRepository transactionRepo,
                             MoolreClient moolreClient,
                             OutboxPublisher outboxPublisher,
                             TransactionReferenceGenerator referenceGenerator,
                             Clock clock,
                             @Value("${stash.ledger.moolre-settlement-account-id:00000000-0000-0000-0000-000000000004}")
                             String settlementAccountId) {
        this.ledgerAccountRepo         = ledgerAccountRepo;
        this.balanceService            = balanceService;
        this.ledgerService             = ledgerService;
        this.transactionRepo           = transactionRepo;
        this.moolreClient              = moolreClient;
        this.outboxPublisher           = outboxPublisher;
        this.referenceGenerator        = referenceGenerator;
        this.clock                     = clock;
        this.moolreSettlementAccountId = UUID.fromString(settlementAccountId);
    }

    @Transactional
    public WithdrawalInitiateResponse initiateWithdrawal(WithdrawalInitiateRequest request,
                                                          String idempotencyKey) {
        LedgerAccountEntity account = ledgerAccountRepo
                .findById(request.ledgerAccountId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Ledger account not found: " + request.ledgerAccountId()));

        if (!"ACTIVE".equals(account.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Account " + request.ledgerAccountId() +
                    " is not ACTIVE (status: " + account.getStatus() + ").");
        }

        if ("USER".equals(account.getOwnerType())
                && !request.userId().equals(account.getOwnerId())) {
            log.warn("Withdrawal ownership mismatch: user={} account_owner={}",
                    request.userId(), account.getOwnerId());
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Account does not belong to the authenticated user.");
        }

        long available = balanceService.computeBalanceWithLock(request.ledgerAccountId());
        if (available < request.amount()) {
            throw new InsufficientBalanceException(available, request.amount());
        }

        String txnReference = generateUniqueReference();

        LedgerWriteCommand reservationCommand = new LedgerWriteCommand(
                "WITHDRAWAL",
                txnReference,
                request.businessReferenceId(),
                request.businessReferenceType(),
                List.of(
                        EntryRequest.of(request.ledgerAccountId(),
                                        EntryDirection.DEBIT, request.amount()),
                        EntryRequest.of(moolreSettlementAccountId,
                                        EntryDirection.CREDIT, request.amount())
                ),
                request.correlationId(),
                "Withdrawal reservation for ref " + txnReference
        );
        ledgerService.writeTransaction(reservationCommand);

        TransactionEntity txn = TransactionEntity.pendingWithdrawal(
                txnReference,
                request.userId(),
                request.ledgerAccountId(),
                request.amount(),
                request.correlationId(),
                idempotencyKey,
                Instant.now(clock)
        );
        transactionRepo.save(txn);

        String channel = MoolreChannelMapper.forTransfer(request.momoProvider());
        String receiverInternational = MomoPhoneFormatter.toInternational(
                request.destinationMomoNumber());
        String amountGhs = MoolreAmountConverter.pesewasToGhsString(request.amount());

        String recipientName = request.recipientName();
        try {
            String validatedName = moolreClient.validateRecipient(channel, receiverInternational);
            if (validatedName != null && !validatedName.isBlank()) {
                recipientName = validatedName;
            }
        } catch (Exception e) {
            log.warn("Moolre recipient validation failed for txn={} — continuing with request name: {}",
                    txnReference, e.getMessage());
        }

        TransferResult transferResponse;
        try {
            transferResponse = moolreClient.initiateTransfer(
                    channel,
                    receiverInternational,
                    amountGhs,
                    txnReference,
                    "Stash withdrawal " + txnReference + " (" + recipientName + ")"
            );
        } catch (MoolreClientException e) {
            reverseReservation(txn, request.correlationId(),
                    "Moolre transfer rejected: " + e.getMessage());
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Moolre rejected the transfer: " + e.getMessage());
        } catch (Exception e) {
            reverseReservation(txn, request.correlationId(),
                    "Moolre transfer failed: " + e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Failed to initiate Moolre transfer: " + e.getMessage());
        }

        String providerTransferCode = transferResponse.transactionId() != null
                ? transferResponse.transactionId()
                : txnReference;
        txn.setExternalReference(providerTransferCode);
        transactionRepo.save(txn);

        if (transferResponse.txStatus() != null && transferResponse.txStatus() == 1) {
            log.info("Withdrawal completed synchronously: ref={} moolreTxnId={} amount={}p user={}",
                    txnReference, providerTransferCode, request.amount(), request.userId());
            txn.markCompleted(null, Instant.now(clock));
            transactionRepo.save(txn);
            outboxPublisher.publish(
                    new WithdrawalCompletedEvent(
                            txn.getId(), null,
                            txn.getInitiatingUserId(),
                            txn.getSourceLedgerAccountId(),
                            request.amount(),
                            txn.getReference(),
                            request.correlationId()),
                    request.correlationId()
            );
            return WithdrawalInitiateResponse.completed(txnReference, providerTransferCode);
        }

        log.info("Withdrawal initiated: ref={} moolreTxnId={} amount={}p user={} correlation={}",
                txnReference, providerTransferCode,
                request.amount(), request.userId(), request.correlationId());

        return WithdrawalInitiateResponse.pending(txnReference, providerTransferCode);
    }

    /**
     * Marks a PENDING withdrawal COMPLETED. Lookup prefers our STSH reference
     * ({@code data.externalref} from Moolre), falling back to
     * {@code external_reference} (Moolre transaction id / legacy Paystack transfer code).
     */
    @Transactional
    public UUID handleTransferSuccess(String lookupKey, long amount, String correlationId) {
        TransactionEntity txn = findWithdrawal(lookupKey);

        if (!"PENDING".equals(txn.getStatus())) {
            log.info("transfer.success: txn {} already in status {} — skipping",
                    txn.getReference(), txn.getStatus());
            return txn.getId();
        }

        txn.markCompleted(null, Instant.now(clock));
        transactionRepo.save(txn);

        outboxPublisher.publish(
                new WithdrawalCompletedEvent(
                        txn.getId(), null,
                        txn.getInitiatingUserId(),
                        txn.getSourceLedgerAccountId(),
                        amount,
                        txn.getReference(),
                        correlationId),
                correlationId
        );

        log.info("transfer.success processed: ref={} lookupKey={} correlation={}",
                txn.getReference(), lookupKey, correlationId);
        return txn.getId();
    }

    @Transactional
    public UUID handleTransferFailed(String lookupKey, String failureReason,
                                      String correlationId) {
        TransactionEntity txn = findWithdrawal(lookupKey);

        if (!"PENDING".equals(txn.getStatus())) {
            log.info("transfer.failed: txn {} already in status {} — skipping",
                    txn.getReference(), txn.getStatus());
            return txn.getId();
        }

        String reversalRef = referenceGenerator.generate();
        LedgerWriteCommand reversal = new LedgerWriteCommand(
                "REVERSAL",
                reversalRef,
                txn.getId(),
                "REVERSAL",
                List.of(
                        EntryRequest.of(txn.getSourceLedgerAccountId(),
                                        EntryDirection.CREDIT, txn.getGrossAmount()),
                        EntryRequest.of(moolreSettlementAccountId,
                                        EntryDirection.DEBIT, txn.getGrossAmount())
                ),
                correlationId,
                "Reversal: Moolre transfer failed — " + failureReason
        );
        LedgerWriteResult reversalResult = ledgerService.writeTransaction(reversal);

        txn.markFailed(Instant.now(clock));
        transactionRepo.save(txn);

        outboxPublisher.publish(
                new WithdrawalFailedEvent(
                        txn.getId(),
                        reversalResult.ledgerTransactionId(),
                        txn.getInitiatingUserId(),
                        txn.getSourceLedgerAccountId(),
                        txn.getGrossAmount(),
                        txn.getReference(),
                        failureReason,
                        correlationId),
                correlationId
        );

        log.info("transfer.failed processed: ref={} lookupKey={} reason={} correlation={}",
                txn.getReference(), lookupKey, failureReason, correlationId);
        return txn.getId();
    }

    private TransactionEntity findWithdrawal(String lookupKey) {
        return transactionRepo.findByReference(lookupKey)
                .or(() -> transactionRepo.findByExternalReference(lookupKey))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No transaction for transfer lookup: " + lookupKey));
    }

    private void reverseReservation(TransactionEntity txn, String correlationId, String reason) {
        log.warn("Reversing withdrawal reservation: ref={} reason={}", txn.getReference(), reason);
        String reversalRef = referenceGenerator.generate();
        LedgerWriteCommand reversal = new LedgerWriteCommand(
                "REVERSAL",
                reversalRef,
                txn.getId(),
                "REVERSAL",
                List.of(
                        EntryRequest.of(txn.getSourceLedgerAccountId(),
                                        EntryDirection.CREDIT, txn.getGrossAmount()),
                        EntryRequest.of(moolreSettlementAccountId,
                                        EntryDirection.DEBIT,  txn.getGrossAmount())
                ),
                correlationId,
                "Reversal of reservation " + txn.getReference() + " — " + reason
        );
        ledgerService.writeTransaction(reversal);
        txn.markFailed(Instant.now(clock));
        transactionRepo.save(txn);
    }

    private String generateUniqueReference() {
        for (int attempt = 0; attempt < 5; attempt++) {
            String candidate = referenceGenerator.generate();
            if (transactionRepo.findByReference(candidate).isEmpty()) return candidate;
        }
        throw new IllegalStateException("Failed to generate unique reference after 5 attempts.");
    }
}
