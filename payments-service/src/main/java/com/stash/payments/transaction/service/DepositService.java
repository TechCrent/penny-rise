package com.stash.payments.transaction.service;

import com.stash.payments.ledger.domain.LedgerAccountEntity;
import com.stash.payments.ledger.repository.LedgerAccountRepository;
import com.stash.payments.ledger.service.TransactionReferenceGenerator;
import com.stash.payments.moolre.amount.MoolreAmountConverter;
import com.stash.payments.moolre.channel.MoolreChannelMapper;
import com.stash.payments.moolre.client.MoolreClient;
import com.stash.payments.moolre.dto.PaymentInitiateResult;
import com.stash.payments.moolre.exception.MoolreClientException;
import com.stash.payments.moolre.phone.MomoPhoneFormatter;
import com.stash.payments.transaction.api.dto.DepositInitiateRequest;
import com.stash.payments.transaction.api.dto.DepositInitiateResponse;
import com.stash.payments.transaction.api.dto.DepositOtpRequest;
import com.stash.payments.transaction.domain.TransactionEntity;
import com.stash.payments.transaction.repository.TransactionRepository;
import com.stash.payments.transaction.validation.MomoNumberValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;

/**
 * Initiates a deposit against a ledger account via Moolre MoMo collection.
 *
 * <p><strong>What this service does:</strong>
 * <ol>
 *   <li>Validates the ledger account exists and is ACTIVE.</li>
 *   <li>Generates a transaction reference (STSH-yyyymm-XXXXXX).</li>
 *   <li>Creates a PENDING {@code transaction.transactions} row.</li>
 *   <li>Calls Moolre payment initiation (single merchant account).</li>
 *   <li>Stores the Moolre session id on the PENDING row and commits.</li>
 *   <li>Returns 202 with MoMo-prompt display text.</li>
 * </ol>
 *
 * <p><strong>What this service does NOT do:</strong>
 * <ul>
 *   <li>Post ledger entries — that happens in the webhook handler when
 *       Moolre confirms payment.</li>
 *   <li>Create a {@code ledger_transactions} row — also the webhook handler.</li>
 * </ul>
 *
 * <p><strong>Moolre call not retried:</strong> {@code initiatePayment} is
 * not retried. If it times out, we do not know whether Moolre received it.
 * The PENDING row is left without an {@code externalReference}. The nightly
 * integrity job flags these as stale PENDING rows after a configurable timeout.
 */
@Service
public class DepositService {

    private static final Logger log = LoggerFactory.getLogger(DepositService.class);

    /**
     * Placeholder sandbox MoMo number — Moolre docs do not publish a known
     * test number (unlike Paystack's 0551234987). Disabled by default via
     * {@code stash.moolre.sandbox.substitute-test-momo-number}. Only enable
     * once a real sandbox test number is confirmed with Moolre.
     */
    private static final String SANDBOX_TEST_MOMO_NUMBER = "0000000000";
    private static final String SANDBOX_TEST_MOMO_PROVIDER = "mtn";

    private static final String MOMO_PROMPT_DISPLAY =
            "Approve the MoMo prompt on your phone";

    private final LedgerAccountRepository       ledgerAccountRepo;
    private final TransactionRepository         transactionRepo;
    private final MoolreClient                  moolreClient;
    private final TransactionReferenceGenerator referenceGenerator;
    private final Clock                         clock;
    private final boolean                       substituteTestMomoNumber;

    public DepositService(LedgerAccountRepository ledgerAccountRepo,
                          TransactionRepository transactionRepo,
                          MoolreClient moolreClient,
                          TransactionReferenceGenerator referenceGenerator,
                          Clock clock,
                          @Value("${stash.moolre.sandbox.substitute-test-momo-number:false}")
                          boolean substituteTestMomoNumber) {
        this.ledgerAccountRepo        = ledgerAccountRepo;
        this.transactionRepo          = transactionRepo;
        this.moolreClient             = moolreClient;
        this.referenceGenerator       = referenceGenerator;
        this.clock                    = clock;
        this.substituteTestMomoNumber = substituteTestMomoNumber;
    }

    /**
     * Initiates a deposit. Returns 202 with MoMo-prompt display text.
     *
     * @param request        validated deposit request from the monolith
     * @param idempotencyKey the Idempotency-Key header value (for audit trail on the row)
     * @throws ResponseStatusException 422 for invalid amount, account state, or OTP required;
     *                                 404 for unknown account;
     *                                 409 for closed account;
     *                                 501 for CARD (not yet implemented)
     */
    @Transactional
    public DepositInitiateResponse initiateDeposit(DepositInitiateRequest request,
                                                    String idempotencyKey) {
        if ("CARD".equalsIgnoreCase(request.paymentMethod())) {
            throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED,
                    "CARD deposits are not implemented in v0.3. Use MOMO.");
        }
        if (!"MOMO".equalsIgnoreCase(request.paymentMethod())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Unsupported payment_method: " + request.paymentMethod() +
                    ". Supported values: MOMO");
        }

        MomoNumberValidator.validate(request.mobileProvider(), request.mobileNumber());

        LedgerAccountEntity account = ledgerAccountRepo
                .findById(request.ledgerAccountId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Ledger account not found: " + request.ledgerAccountId()));

        if (!"ACTIVE".equals(account.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ledger account " + request.ledgerAccountId() +
                    " is not ACTIVE (current status: " + account.getStatus() + ").");
        }

        // USER-owned accounts: verify ownership. VAULT accounts: monolith already validated.
        if ("USER".equals(account.getOwnerType())
                && !request.userId().equals(account.getOwnerId())) {
            log.warn("Deposit ownership mismatch: requesting user={} account owner={}",
                    request.userId(), account.getOwnerId());
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Ledger account does not belong to the authenticated user.");
        }

        String txnReference = generateUniqueReference();

        TransactionEntity txn = TransactionEntity.pendingDeposit(
                txnReference, request.userId(), request.amount(),
                request.ledgerAccountId(),
                request.correlationId(), idempotencyKey, Instant.now(clock));
        transactionRepo.save(txn);

        String effectiveMobileNumber = request.mobileNumber();
        String effectiveMobileProvider = request.mobileProvider();
        if (substituteTestMomoNumber && moolreClient.isSandbox()) {
            log.info("[SANDBOX_MOMO_SUBSTITUTION] Charging placeholder Moolre sandbox test MoMo " +
                    "number instead of the user-entered number for txn={} — internal records and " +
                    "the UI still show the real number. Confirm a real Moolre sandbox test number " +
                    "before relying on this flag.", txnReference);
            effectiveMobileNumber = SANDBOX_TEST_MOMO_NUMBER;
            effectiveMobileProvider = SANDBOX_TEST_MOMO_PROVIDER;
        }

        String channel = MoolreChannelMapper.forPayment(effectiveMobileProvider);
        String payerInternational = MomoPhoneFormatter.toInternational(effectiveMobileNumber);
        String amountGhs = MoolreAmountConverter.pesewasToGhsString(request.amount());

        PaymentInitiateResult paymentResult;
        try {
            paymentResult = moolreClient.initiatePayment(
                    channel, payerInternational, amountGhs, txnReference, txnReference);
        } catch (MoolreClientException e) {
            log.error("Moolre rejected payment initiation: status={} message={}",
                    e.getHttpStatus(), e.getMessage());
            txn.markFailed(Instant.now(clock));
            transactionRepo.save(txn);
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Moolre rejected the charge: " + e.getMessage());
        }
        // 5xx or timeout: let the exception propagate. The PENDING row stays.

        if ("TP14".equalsIgnoreCase(paymentResult.code())) {
            txn.setExternalReference(txnReference);
            transactionRepo.save(txn);
            log.info("Moolre OTP required (TP14) for txn={} — awaiting user OTP",
                    txnReference);
            return DepositInitiateResponse.otpRequired(txnReference, txnReference);
        }

        if (!"TR099".equalsIgnoreCase(paymentResult.code())) {
            log.error("Unexpected Moolre payment code={} message={} txn={}",
                    paymentResult.code(), paymentResult.message(), txnReference);
            txn.markFailed(Instant.now(clock));
            transactionRepo.save(txn);
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Moolre rejected the charge: " +
                    (paymentResult.message() != null ? paymentResult.message() : paymentResult.code()));
        }

        String providerRef = paymentResult.sessionId() != null && !paymentResult.sessionId().isBlank()
                ? paymentResult.sessionId()
                : txnReference;
        txn.setExternalReference(providerRef);
        transactionRepo.save(txn);

        log.info("Deposit initiated: ref={} moolreSession={} amount={}p user={} correlation={}",
                txnReference, providerRef,
                request.amount(), request.userId(), request.correlationId());

        return DepositInitiateResponse.pending(
                txnReference,
                MOMO_PROMPT_DISPLAY,
                providerRef
        );
    }

    /**
     * Completes a PENDING deposit that required Moolre SMS OTP ({@code TP14}).
     *
     * <p>Observed sandbox sequence: OTP submit → {@code TP17} (verified) →
     * resubmit with same OTP → {@code TR099} (USSD/MoMo prompt). We perform
     * both steps here so the mobile app only collects the code once.
     */
    @Transactional
    public DepositInitiateResponse completeDepositOtp(String transactionReference,
                                                      DepositOtpRequest request) {
        MomoNumberValidator.validate(request.mobileProvider(), request.mobileNumber());

        TransactionEntity txn = transactionRepo.findByReference(transactionReference)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Deposit not found: " + transactionReference));

        if (!"DEPOSIT".equals(txn.getTransactionType())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Transaction is not a deposit.");
        }
        if (!"PENDING".equals(txn.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Deposit is not PENDING (status: " + txn.getStatus() + ").");
        }
        if (!request.userId().equals(txn.getInitiatingUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Deposit does not belong to the authenticated user.");
        }

        String otp = request.otpCode() == null ? "" : request.otpCode().trim();
        if (otp.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "otp_code is required.");
        }

        String channel = MoolreChannelMapper.forPayment(request.mobileProvider());
        String payerInternational = MomoPhoneFormatter.toInternational(request.mobileNumber());
        String amountGhs = MoolreAmountConverter.pesewasToGhsString(txn.getGrossAmount());

        PaymentInitiateResult result;
        try {
            result = moolreClient.initiatePaymentWithOtp(
                    channel, payerInternational, amountGhs,
                    transactionReference, transactionReference, otp);
        } catch (MoolreClientException e) {
            log.error("Moolre rejected OTP completion: status={} message={} ref={}",
                    e.getHttpStatus(), e.getMessage(), transactionReference);
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Moolre rejected the OTP: " + e.getMessage());
        }

        // Phone verified — push the MoMo prompt with the same OTP (sandbox sequence).
        if ("TP17".equalsIgnoreCase(result.code())) {
            log.info("Moolre TP17 phone verified for ref={} — re-initiating payment with OTP",
                    transactionReference);
            try {
                result = moolreClient.initiatePaymentWithOtp(
                        channel, payerInternational, amountGhs,
                        transactionReference, transactionReference, otp);
            } catch (MoolreClientException e) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "Moolre rejected post-OTP payment: " + e.getMessage());
            }
        }

        if ("TP14".equalsIgnoreCase(result.code())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "OTP was not accepted. Check the code and try again.");
        }

        if (!"TR099".equalsIgnoreCase(result.code())) {
            // Payment may already have succeeded synchronously in some sandbox cases.
            try {
                var status = moolreClient.queryStatus(transactionReference, true);
                if (status.txStatus() != null && status.txStatus() == 1) {
                    log.info("Deposit OTP path: Moolre already success for ref={}", transactionReference);
                    return DepositInitiateResponse.pending(
                            transactionReference, MOMO_PROMPT_DISPLAY, transactionReference);
                }
            } catch (Exception ignored) {
                // fall through to rejection
            }
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Moolre rejected the charge after OTP: " +
                    (result.message() != null ? result.message() : result.code()));
        }

        String providerRef = result.sessionId() != null && !result.sessionId().isBlank()
                ? result.sessionId()
                : transactionReference;
        txn.setExternalReference(providerRef);
        transactionRepo.save(txn);

        log.info("Deposit OTP completed: ref={} moolreSession={} user={}",
                transactionReference, providerRef, request.userId());

        return DepositInitiateResponse.pending(
                transactionReference,
                MOMO_PROMPT_DISPLAY,
                providerRef
        );
    }

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
