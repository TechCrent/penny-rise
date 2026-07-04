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
import com.stash.payments.paystack.client.PaystackClient;
import com.stash.payments.paystack.dto.TransferInitiateRequest;
import com.stash.payments.paystack.dto.TransferInitiateResponse;
import com.stash.payments.paystack.dto.TransferRecipientCreateRequest;
import com.stash.payments.paystack.dto.TransferRecipientCreateResponse;
import com.stash.payments.paystack.exception.PaystackClientException;
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
 * Initiates a withdrawal.
 *
 * <p><strong>Steps:</strong>
 * <ol>
 *   <li>Validate account exists, is ACTIVE, and belongs to the user.</li>
 *   <li>Acquire a SELECT FOR UPDATE lock on the account row.</li>
 *   <li>Compute balance inside the lock.</li>
 *   <li>Reject with 422 if insufficient balance.</li>
 *   <li>Post ledger entries (DEBIT source, CREDIT PAYSTACK_SETTLEMENT) — the
 *       reservation. This debits the balance immediately.</li>
 *   <li>Create a Paystack transfer recipient for the MoMo number.</li>
 *   <li>Initiate the Paystack transfer.</li>
 *   <li>Store the transfer reference; return 202 PENDING.</li>
 * </ol>
 *
 * <p><strong>On Paystack failure after ledger write:</strong> the ledger entries
 * are already committed (the reservation is in place). The transaction stays PENDING.
 * The webhook handler ({@code transfer.failed}) reverses the entries via a
 * compensating transaction. If the Paystack call never returns a webhook
 * (timeout, Paystack outage), the nightly integrity job flags the stale PENDING
 * row for ops review.
 *
 * <p><strong>Concurrency:</strong> the SELECT FOR UPDATE lock on the account row
 * serialises concurrent withdrawals. Two simultaneous requests both reach Step 2;
 * one acquires the lock and proceeds; the second blocks until the first commits,
 * then computes the updated (already-debited) balance.
 */
@Service
public class WithdrawalService {

    private static final Logger log = LoggerFactory.getLogger(WithdrawalService.class);

    private final LedgerAccountRepository       ledgerAccountRepo;
    private final BalanceService                balanceService;
    private final LedgerService                 ledgerService;
    private final TransactionRepository         transactionRepo;
    private final PaystackClient                paystackClient;
    private final OutboxPublisher               outboxPublisher;
    private final TransactionReferenceGenerator referenceGenerator;
    private final Clock                         clock;
    private final UUID                          paystackSettlementAccountId;
    private final boolean                       simulateSandboxBlockedTransfers;

    /**
     * Substring of Paystack's rejection message when a sandbox/unverified-business
     * account attempts a third-party payout. This is an account-tier restriction on
     * Paystack's side (distinct from KYC/subaccount verification) — real production
     * merchant accounts past business registration don't hit it. Only THIS specific
     * rejection is eligible for simulation; every other Paystack error still reverses
     * the reservation and fails normally.
     */
    private static final String SANDBOX_STARTER_BUSINESS_MARKER =
            "third party payouts as a starter business";

    public WithdrawalService(LedgerAccountRepository ledgerAccountRepo,
                             BalanceService balanceService,
                             LedgerService ledgerService,
                             TransactionRepository transactionRepo,
                             PaystackClient paystackClient,
                             OutboxPublisher outboxPublisher,
                             TransactionReferenceGenerator referenceGenerator,
                             Clock clock,
                             @Value("${stash.ledger.paystack-settlement-account-id}")
                             String settlementAccountId,
                             @Value("${stash.paystack.sandbox.simulate-blocked-transfers:false}")
                             boolean simulateSandboxBlockedTransfers) {
        this.ledgerAccountRepo           = ledgerAccountRepo;
        this.balanceService              = balanceService;
        this.ledgerService               = ledgerService;
        this.transactionRepo             = transactionRepo;
        this.paystackClient              = paystackClient;
        this.outboxPublisher             = outboxPublisher;
        this.referenceGenerator          = referenceGenerator;
        this.clock                       = clock;
        this.simulateSandboxBlockedTransfers = simulateSandboxBlockedTransfers;
        this.paystackSettlementAccountId = UUID.fromString(settlementAccountId);
    }

    @Transactional
    public WithdrawalInitiateResponse initiateWithdrawal(WithdrawalInitiateRequest request,
                                                          String idempotencyKey) {
        // ── Validate account ──────────────────────────────────────────────
        LedgerAccountEntity account = ledgerAccountRepo
                .findById(request.ledgerAccountId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Ledger account not found: " + request.ledgerAccountId()));

        if (!"ACTIVE".equals(account.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Account " + request.ledgerAccountId() +
                    " is not ACTIVE (status: " + account.getStatus() + ").");
        }

        // USER-owned accounts (direct wallet withdrawals) carry owner_id = the user
        // themselves, so ownership can be verified directly here. VAULT (and other
        // entity) accounts carry owner_id = the ENTITY's id (e.g. the vault UUID),
        // NOT the user's — see PaymentsWithdrawalClient, which passes the vault's
        // ledger_account_id alongside the initiating user_id. For those, the monolith
        // is the authority on whether the user owns the entity and has already
        // validated it (VaultWithdrawalService) before calling this internal endpoint;
        // Payments cannot independently re-derive that mapping. Applying the direct
        // owner_id == userId check to VAULT accounts would 403 every vault withdrawal
        // (mirrors the same fix already applied to DepositService).
        if ("USER".equals(account.getOwnerType())
                && !request.userId().equals(account.getOwnerId())) {
            log.warn("Withdrawal ownership mismatch: user={} account_owner={}",
                    request.userId(), account.getOwnerId());
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Account does not belong to the authenticated user.");
        }

        // ── Balance check with SELECT FOR UPDATE lock ─────────────────────
        long available = balanceService.computeBalanceWithLock(request.ledgerAccountId());
        if (available < request.amount()) {
            throw new InsufficientBalanceException(available, request.amount());
        }

        // ── Generate reference ────────────────────────────────────────────
        String txnReference = generateUniqueReference();

        // ── Post reservation entries (ledger write) ───────────────────────
        LedgerWriteCommand reservationCommand = new LedgerWriteCommand(
                "WITHDRAWAL",
                txnReference,
                request.businessReferenceId(),
                request.businessReferenceType(),
                List.of(
                        EntryRequest.of(request.ledgerAccountId(),
                                        EntryDirection.DEBIT, request.amount()),
                        EntryRequest.of(paystackSettlementAccountId,
                                        EntryDirection.CREDIT, request.amount())
                ),
                request.correlationId(),
                "Withdrawal reservation for ref " + txnReference
        );
        LedgerWriteResult reservation = ledgerService.writeTransaction(reservationCommand);

        // ── Create PENDING transaction row ────────────────────────────────
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

        // ── Create Paystack transfer recipient ────────────────────────────
        String bankCode = momoProviderToBankCode(request.momoProvider());
        TransferRecipientCreateResponse recipientResponse;
        try {
            recipientResponse = paystackClient.createTransferRecipient(
                    new TransferRecipientCreateRequest(
                            "mobile_money",
                            request.recipientName(),
                            request.destinationMomoNumber(),
                            bankCode,
                            "GHS"
                    )
            );
        } catch (Exception e) {
            reverseReservation(txn, request.correlationId(),
                    "Paystack recipient creation failed: " + e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Failed to create Paystack transfer recipient: " + e.getMessage());
        }

        if (!recipientResponse.status()) {
            reverseReservation(txn, request.correlationId(),
                    "Paystack rejected recipient: " + recipientResponse.message());
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Paystack rejected the transfer recipient: " + recipientResponse.message());
        }

        // ── Initiate Paystack transfer ────────────────────────────────────
        TransferInitiateResponse transferResponse;
        try {
            transferResponse = paystackClient.initiateTransfer(
                    new TransferInitiateRequest(
                            "balance",
                            request.amount(),
                            recipientResponse.data().recipientCode(),
                            "Stash withdrawal " + txnReference,
                            txnReference,
                            "GHS"
                    )
            );
        } catch (PaystackClientException e) {
            if (simulateSandboxBlockedTransfers
                    && e.getMessage() != null
                    && e.getMessage().contains(SANDBOX_STARTER_BUSINESS_MARKER)) {
                return completeSandboxSimulatedWithdrawal(
                        txn, reservation.ledgerTransactionId(), request, e.getMessage());
            }
            reverseReservation(txn, request.correlationId(),
                    "Paystack transfer rejected: " + e.getMessage());
            txn.markFailed(Instant.now(clock));
            transactionRepo.save(txn);
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Paystack rejected the transfer: " + e.getMessage());
        }

        txn.setExternalReference(transferResponse.data().transferCode());
        transactionRepo.save(txn);

        log.info("Withdrawal initiated: ref={} transferCode={} amount={}p user={} correlation={}",
                txnReference, transferResponse.data().transferCode(),
                request.amount(), request.userId(), request.correlationId());

        return WithdrawalInitiateResponse.pending(
                txnReference, transferResponse.data().transferCode());
    }

    // ── Transfer webhook handlers ─────────────────────────────────────────

    /**
     * Handles {@code transfer.success}: marks the transaction COMPLETED
     * and emits the outbox event. No new ledger entries — the reservation
     * entries already reflect the movement correctly.
     */
    @Transactional
    public UUID handleTransferSuccess(String transferCode, long amount, String correlationId) {
        TransactionEntity txn = transactionRepo.findByExternalReference(transferCode)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No transaction for transfer code: " + transferCode));

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

        log.info("transfer.success processed: ref={} transferCode={} correlation={}",
                txn.getReference(), transferCode, correlationId);
        return txn.getId();
    }

    /**
     * Handles {@code transfer.failed}: writes a compensating reversal
     * ledger transaction to restore the debited balance, marks the
     * transaction FAILED, and emits the outbox event.
     */
    @Transactional
    public UUID handleTransferFailed(String transferCode, String failureReason,
                                      String correlationId) {
        TransactionEntity txn = transactionRepo.findByExternalReference(transferCode)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No transaction for transfer code: " + transferCode));

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
                        EntryRequest.of(paystackSettlementAccountId,
                                        EntryDirection.DEBIT, txn.getGrossAmount())
                ),
                correlationId,
                "Reversal: Paystack transfer failed — " + failureReason
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

        log.info("transfer.failed processed: ref={} transferCode={} reason={} correlation={}",
                txn.getReference(), transferCode, failureReason, correlationId);
        return txn.getId();
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    /**
     * Treats a withdrawal as COMPLETED without a real Paystack payout, when Paystack
     * rejected it purely due to the sandbox/unverified-business account-tier
     * restriction ({@link #SANDBOX_STARTER_BUSINESS_MARKER}) and
     * {@code stash.paystack.sandbox.simulate-blocked-transfers} is explicitly enabled.
     *
     * <p><strong>Must never run in production</strong> — it marks the reservation as
     * final without Paystack's confirmation. The flag defaults false and is only ever
     * set in local dev config (never in application-prod.yml or any deployed
     * environment). {@code external_reference} is tagged {@code "SANDBOX-SIMULATED"}
     * (not a real transfer code) so the row stays permanently distinguishable from a
     * genuine Paystack-confirmed payout, and the WARN log line below makes it visible
     * to any log-based audit review. The normal {@link WithdrawalCompletedEvent} still
     * fires so downstream consumers (notifications, statements, audit-service) behave
     * exactly as for a real completion.
     */
    private WithdrawalInitiateResponse completeSandboxSimulatedWithdrawal(
            TransactionEntity txn, UUID ledgerTransactionId,
            WithdrawalInitiateRequest request, String paystackReason) {

        log.warn("[SANDBOX_SIMULATED_WITHDRAWAL] Paystack blocked this transfer with the sandbox " +
                 "account-tier restriction ('{}'). stash.paystack.sandbox.simulate-blocked-transfers " +
                 "is enabled, so completing without a real Paystack payout. ref={} amount={}p user={} " +
                 "correlation={}. This flag must never be enabled outside local/sandbox dev.",
                paystackReason, txn.getReference(), request.amount(), request.userId(),
                request.correlationId());

        txn.setExternalReference("SANDBOX-SIMULATED");
        txn.markCompleted(ledgerTransactionId, Instant.now(clock));
        transactionRepo.save(txn);

        outboxPublisher.publish(
                new WithdrawalCompletedEvent(
                        txn.getId(), ledgerTransactionId, txn.getInitiatingUserId(),
                        txn.getSourceLedgerAccountId(), txn.getGrossAmount(),
                        txn.getReference(), request.correlationId()),
                request.correlationId()
        );

        return WithdrawalInitiateResponse.completed(txn.getReference(), "SANDBOX-SIMULATED");
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
                        EntryRequest.of(paystackSettlementAccountId,
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

    private static String momoProviderToBankCode(String provider) {
        return switch (provider.toLowerCase()) {
            case "mtn"        -> "MTN";
            case "vodafone"   -> "VDF";
            case "airteltigo" -> "ATL";
            default -> throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "Unsupported MoMo provider: " + provider +
                    ". Supported: mtn, vodafone, airteltigo");
        };
    }
}
