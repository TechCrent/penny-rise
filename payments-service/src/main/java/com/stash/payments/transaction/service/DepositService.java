package com.stash.payments.transaction.service;

import com.stash.payments.ledger.domain.LedgerAccountEntity;
import com.stash.payments.ledger.repository.LedgerAccountRepository;
import com.stash.payments.ledger.service.TransactionReferenceGenerator;
import com.stash.payments.paystack.client.PaystackClient;
import com.stash.payments.paystack.dto.ChargeInitiateRequest;
import com.stash.payments.paystack.dto.ChargeInitiateResponse;
import com.stash.payments.paystack.exception.PaystackClientException;
import com.stash.payments.paystack.repository.PaystackSubaccountRepository;
import com.stash.payments.transaction.api.dto.DepositInitiateRequest;
import com.stash.payments.transaction.api.dto.DepositInitiateResponse;
import com.stash.payments.transaction.domain.TransactionEntity;
import com.stash.payments.transaction.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;

/**
 * Initiates a deposit against a ledger account.
 *
 * <p><strong>What this service does:</strong>
 * <ol>
 *   <li>Validates the ledger account exists and is ACTIVE.</li>
 *   <li>Resolves the Paystack subaccount code for the account.</li>
 *   <li>Generates a transaction reference (STSH-yyyymm-XXXXXX).</li>
 *   <li>Creates a PENDING {@code transaction.transactions} row.</li>
 *   <li>Calls Paystack charge initialisation.</li>
 *   <li>Stores the Paystack reference on the PENDING row and commits.</li>
 *   <li>Returns 202 with the authorisation URL.</li>
 * </ol>
 *
 * <p><strong>What this service does NOT do:</strong>
 * <ul>
 *   <li>Post ledger entries — that happens in the webhook handler when
 *       Paystack confirms payment.</li>
 *   <li>Create a {@code ledger_transactions} row — also the webhook handler.</li>
 * </ul>
 *
 * <p><strong>Paystack call not retried:</strong> {@code initiateCharge} is
 * not idempotent from Paystack's perspective. If it times out, we do not know
 * whether Paystack received it. The PENDING row is left without an
 * {@code externalReference}. The nightly integrity job (v0.3-022) flags these
 * as stale PENDING rows after a configurable timeout.
 */
@Service
public class DepositService {

    private static final Logger log = LoggerFactory.getLogger(DepositService.class);

    private final LedgerAccountRepository      ledgerAccountRepo;
    private final PaystackSubaccountRepository subaccountRepo;
    private final TransactionRepository        transactionRepo;
    private final PaystackClient               paystackClient;
    private final TransactionReferenceGenerator referenceGenerator;
    private final Clock                        clock;

    public DepositService(LedgerAccountRepository ledgerAccountRepo,
                          PaystackSubaccountRepository subaccountRepo,
                          TransactionRepository transactionRepo,
                          PaystackClient paystackClient,
                          TransactionReferenceGenerator referenceGenerator,
                          Clock clock) {
        this.ledgerAccountRepo   = ledgerAccountRepo;
        this.subaccountRepo      = subaccountRepo;
        this.transactionRepo     = transactionRepo;
        this.paystackClient      = paystackClient;
        this.referenceGenerator  = referenceGenerator;
        this.clock               = clock;
    }

    /**
     * Initiates a deposit. Returns 202 with the authorisation URL.
     *
     * @param request       validated deposit request from the monolith
     * @param idempotencyKey the Idempotency-Key header value (for audit trail on the row)
     * @throws ResponseStatusException 422 for invalid amount or account state;
     *                                 404 for unknown account;
     *                                 409 for closed account;
     *                                 501 for CARD (not yet implemented)
     */
    @Transactional
    public DepositInitiateResponse initiateDeposit(DepositInitiateRequest request,
                                                    String idempotencyKey) {
        // ── Validate payment method ───────────────────────────────────────
        if ("CARD".equalsIgnoreCase(request.paymentMethod())) {
            throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED,
                    "CARD deposits are not implemented in v0.3. Use MOMO.");
        }
        if (!"MOMO".equalsIgnoreCase(request.paymentMethod())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Unsupported payment_method: " + request.paymentMethod() +
                    ". Supported values: MOMO");
        }

        // ── Validate MoMo fields ──────────────────────────────────────────
        if (request.mobileNumber() == null || request.mobileNumber().isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "mobile_number is required for MOMO deposits.");
        }
        if (request.mobileProvider() == null || request.mobileProvider().isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "mobile_provider is required for MOMO deposits.");
        }

        // ── Validate ledger account ───────────────────────────────────────
        LedgerAccountEntity account = ledgerAccountRepo
                .findById(request.ledgerAccountId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Ledger account not found: " + request.ledgerAccountId()));

        if (!"ACTIVE".equals(account.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ledger account " + request.ledgerAccountId() +
                    " is not ACTIVE (current status: " + account.getStatus() + ").");
        }

        // ── Validate ownership (account must belong to the requesting user) ──
        if (!request.userId().equals(account.getOwnerId())) {
            log.warn("Deposit ownership mismatch: requesting user={} account owner={}",
                    request.userId(), account.getOwnerId());
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Ledger account does not belong to the authenticated user.");
        }

        // ── Resolve Paystack subaccount code ──────────────────────────────
        String subaccountCode = subaccountRepo
                .findByOwnerTypeAndOwnerId("USER", request.userId())
                .map(sub -> sub.getPaystackSubaccountCode())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                        "No Paystack subaccount found for user " + request.userId() +
                        ". KYC may not be complete or provisioning is still in progress."));

        // ── Generate reference ────────────────────────────────────────────
        String txnReference = generateUniqueReference();

        // ── Create PENDING transaction row ────────────────────────────────
        TransactionEntity txn = TransactionEntity.pendingDeposit(
                txnReference, request.userId(), request.amount(),
                request.correlationId(), idempotencyKey, Instant.now(clock));
        transactionRepo.save(txn);

        // ── Call Paystack ─────────────────────────────────────────────────
        // NOT retried — non-idempotent. See class javadoc.
        ChargeInitiateRequest chargeRequest = new ChargeInitiateRequest(
                request.customerEmail(),
                request.amount(),
                new ChargeInitiateRequest.MobileMoneyChannel(
                        request.mobileNumber(), request.mobileProvider()),
                "GHS",
                subaccountCode
        );

        ChargeInitiateResponse chargeResponse;
        try {
            chargeResponse = paystackClient.initiateCharge(chargeRequest);
        } catch (PaystackClientException e) {
            // 4xx from Paystack — client error, not retryable. Mark FAILED immediately.
            log.error("Paystack rejected charge initiation: status={} message={}",
                    e.getHttpStatus(), e.getMessage());
            txn.markFailed(Instant.now(clock));
            transactionRepo.save(txn);
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Paystack rejected the charge: " + e.getMessage());
        }
        // 5xx or timeout: let the exception propagate. The PENDING row stays.
        // The nightly integrity job flags it as stale after 30 minutes.

        // ── Store Paystack reference ──────────────────────────────────────
        txn.setExternalReference(chargeResponse.data().reference());
        transactionRepo.save(txn);

        log.info("Deposit initiated: ref={} paystackRef={} amount={}p user={} correlation={}",
                txnReference, chargeResponse.data().reference(),
                request.amount(), request.userId(), request.correlationId());

        // Paystack sandbox always returns null for authorisation_url on MoMo —
        // the user completes on their phone, not a URL. Return display_text instead
        // when authorisation_url is absent.
        String authUrl = chargeResponse.data() != null
                ? chargeResponse.data().displayText()
                : null;

        return DepositInitiateResponse.pending(
                txnReference,
                authUrl,
                chargeResponse.data().reference()
        );
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    /**
     * Generates a unique transaction reference with collision retry.
     * See v0.3-002 tracking item: the caller retries on DB UNIQUE constraint violation.
     */
    private String generateUniqueReference() {
        for (int attempt = 0; attempt < 5; attempt++) {
            String candidate = referenceGenerator.generate();
            if (transactionRepo.findByReference(candidate).isEmpty()) {
                return candidate;
            }
            log.warn("Transaction reference collision on attempt {}: {}", attempt + 1, candidate);
        }
        throw new IllegalStateException(
                "Failed to generate a unique transaction reference after 5 attempts. " +
                "This indicates a serious collision rate problem.");
    }
}
