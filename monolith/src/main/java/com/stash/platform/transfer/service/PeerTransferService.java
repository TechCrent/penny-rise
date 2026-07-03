package com.stash.platform.transfer.service;

import com.stash.platform.subscription.policy.SubscriptionPolicy;
import com.stash.platform.transfer.api.dto.CreateTransferRequest;
import com.stash.platform.transfer.api.dto.CreateTransferResponse;
import com.stash.platform.transfer.client.PeerTransferPaymentsClient;
import com.stash.platform.transfer.client.TransferPaymentsException;
import com.stash.platform.transfer.domain.MonthlyTransferQuotaEntity;
import com.stash.platform.transfer.domain.PeerTransferEntity;
import com.stash.platform.transfer.repository.MonthlyTransferQuotaRepository;
import com.stash.platform.transfer.repository.PeerTransferRepository;
import com.stash.platform.user.domain.User;
import com.stash.platform.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@Service
public class PeerTransferService {

    private static final Logger log = LoggerFactory.getLogger(PeerTransferService.class);

    private final PeerTransferRepository         transferRepo;
    private final MonthlyTransferQuotaRepository quotaRepo;
    private final UserRepository                 userRepo;
    private final PeerTransferPaymentsClient     paymentsClient;
    private final SubscriptionPolicy             subscriptionPolicy;
    private final Clock                          clock;
    private final long                           overflowFeePesewas;

    public PeerTransferService(
            PeerTransferRepository transferRepo,
            MonthlyTransferQuotaRepository quotaRepo,
            UserRepository userRepo,
            PeerTransferPaymentsClient paymentsClient,
            SubscriptionPolicy subscriptionPolicy,
            Clock clock,
            @Value("${stash.transfer.overflow-fee-pesewas:200}")
                    long overflowFeePesewas) {
        this.transferRepo       = transferRepo;
        this.quotaRepo          = quotaRepo;
        this.userRepo           = userRepo;
        this.paymentsClient     = paymentsClient;
        this.subscriptionPolicy = subscriptionPolicy;
        this.clock              = clock;
        this.overflowFeePesewas = overflowFeePesewas;
    }

    @Transactional
    public CreateTransferResponse transfer(UUID senderId, CreateTransferRequest request,
                                            String correlationId, String idempotencyKey) {
        // ── Idempotency: check for an existing transfer with this key ─────
        var existing = transferRepo.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            PeerTransferEntity prior = existing.get();
            if ("COMPLETED".equals(prior.getStatus())) {
                log.info("PeerTransfer: returning cached COMPLETED for idempotencyKey={}", idempotencyKey);
                return buildCachedResponse(prior);
            }
            if ("PENDING".equals(prior.getStatus())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "TRANSFER_IN_PROGRESS: A transfer with this idempotency key is " +
                        "already in progress. Retry after a moment.");
            }
        }

        UUID recipientId = request.recipientUserId();

        // ── Self-transfer guard ───────────────────────────────────────────
        if (senderId.equals(recipientId)) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "TRANSFER_TO_SELF: You cannot transfer money to yourself.");
        }

        // ── Validate sender (KYC) ─────────────────────────────────────────
        User sender = userRepo.findById(senderId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.INTERNAL_SERVER_ERROR, "Authenticated user not found."));
        if (!sender.isKycApproved()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "KYC_NOT_APPROVED: Complete identity verification before sending transfers.");
        }

        // ── Validate recipient ────────────────────────────────────────────
        User recipient = userRepo.findById(recipientId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "RECIPIENT_NOT_FOUND: No Stash user found with that ID."));
        if (!recipient.isKycApproved()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "RECIPIENT_NOT_VERIFIED: The recipient has not completed " +
                    "identity verification and cannot receive transfers.");
        }

        // ── Atomic quota check and increment ──────────────────────────────
        LocalDate today = LocalDate.now(clock);
        int year  = today.getYear();
        int month = today.getMonthValue();

        // Atomically ensure a quota row exists before locking it — ON CONFLICT DO NOTHING
        // means concurrent first-transfers both succeed here without a unique-constraint 500.
        quotaRepo.insertIfAbsent(UUID.randomUUID(), senderId, year, month);
        MonthlyTransferQuotaEntity quota = quotaRepo
                .findByUserAndMonthForUpdate(senderId, year, month)
                .orElseThrow(() -> new IllegalStateException(
                        "Quota row missing after insertIfAbsent for user=" + senderId));

        // v0.5-030: quota limit is tier-aware — FREE=5/month, PREMIUM=20/month
        // (SubscriptionPolicy), both charged overflowFeePesewas beyond quota.
        int     monthlyLimit  = subscriptionPolicy.transfersPerMonth(sender.getSubscriptionTier());
        boolean isFree        = quota.consumeOneTransfer(monthlyLimit);
        long    feeAmount     = isFree ? 0L : overflowFeePesewas;
        int     freeRemaining = Math.max(0, monthlyLimit - quota.getFreeTransfersUsed());
        quotaRepo.save(quota);

        // ── Create PENDING transfer row ────────────────────────────────────
        Instant now = Instant.now(clock);
        PeerTransferEntity transfer = PeerTransferEntity.create(
                senderId, recipientId, request.amount(), feeAmount,
                request.narrative(), idempotencyKey, now);
        transfer = transferRepo.save(transfer);

        UUID transferId = transfer.getId();
        log.info("PeerTransfer PENDING: id={} sender={} recipient={} amount={}p fee={}p " +
                 "isFree={} correlation={}",
                transferId, senderId, recipientId,
                request.amount(), feeAmount, isFree, correlationId);

        // ── Resolve USER_WALLET IDs ───────────────────────────────────────
        UUID senderWalletId, recipientWalletId;
        try {
            senderWalletId    = paymentsClient.resolveUserWallet(senderId, correlationId);
            recipientWalletId = paymentsClient.resolveUserWallet(recipientId, correlationId);
        } catch (TransferPaymentsException e) {
            transfer.fail(now);
            transferRepo.save(transfer);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Payment service unavailable. Please try again.");
        }

        // ── Leg 1: principal transfer ─────────────────────────────────────
        String principalTxnRef;
        try {
            principalTxnRef = paymentsClient.transferPrincipal(
                    senderWalletId, recipientWalletId,
                    request.amount(), transferId,
                    request.narrative(), correlationId,
                    "peer-transfer-" + transferId);
        } catch (TransferPaymentsException e) {
            transfer.fail(now);
            transferRepo.save(transfer);
            translateAndThrow(e, "principal transfer");
            throw e; // unreachable
        }

        // ── Leg 2: fee transfer (only if fee applies) ─────────────────────
        if (feeAmount > 0) {
            try {
                paymentsClient.transferFee(
                        senderWalletId, feeAmount, transferId,
                        correlationId, "peer-transfer-fee-" + transferId);
            } catch (TransferPaymentsException e) {
                // Principal succeeded; fee failed. Mark COMPLETED but flag for manual recovery.
                log.error("[P0_ALERT] Fee transfer failed after principal succeeded: " +
                          "transferId={} feeAmount={}p sender={} correlation={}. " +
                          "Manual fee recovery required.",
                        transferId, feeAmount, senderId, correlationId);
            }
        }

        // ── Mark COMPLETED ────────────────────────────────────────────────
        transfer.complete(UUID.fromString(principalTxnRef), isFree, now);
        transferRepo.save(transfer);

        log.info("PeerTransfer COMPLETED: id={} txnRef={} amount={}p fee={}p isFree={} " +
                 "freeRemaining={} correlation={}",
                transferId, principalTxnRef, request.amount(),
                feeAmount, isFree, freeRemaining, correlationId);

        return CreateTransferResponse.of(
                transferId, principalTxnRef,
                request.amount(), feeAmount, freeRemaining,
                recipientId, now);
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private CreateTransferResponse buildCachedResponse(PeerTransferEntity prior) {
        return CreateTransferResponse.of(
                prior.getId(),
                prior.getTransactionId() != null ? prior.getTransactionId().toString() : null,
                prior.getAmount(), prior.getFeeAmount(), 0,
                prior.getRecipientUserId(), prior.getCompletedAt());
    }

    public Optional<PeerTransferEntity> findById(UUID transferId) {
        return transferRepo.findById(transferId);
    }

    private void translateAndThrow(TransferPaymentsException e, String leg) {
        String body = e.getMessage();
        if (body != null && body.contains("422") &&
                body.contains("PAYMENTS_INSUFFICIENT_BALANCE")) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "TRANSFER_INSUFFICIENT_BALANCE: Your wallet balance is too low " +
                    "to complete this transfer.");
        }
        log.error("PeerTransfer: {} Payments failure: {}", leg, body);
        throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                "Payment service error. Please try again.");
    }
}
