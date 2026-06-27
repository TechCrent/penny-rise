package com.stash.payments.webhook.service;

import com.stash.payments.ledger.domain.EntryDirection;
import com.stash.payments.ledger.domain.EntryRequest;
import com.stash.payments.ledger.service.LedgerWriteCommand;
import com.stash.payments.ledger.service.LedgerWriteResult;
import com.stash.payments.ledger.service.LedgerService;
import com.stash.payments.ledger.repository.LedgerAccountRepository;
import com.stash.payments.outbox.service.OutboxPublisher;
import com.stash.payments.transaction.domain.TransactionEntity;
import com.stash.payments.transaction.event.DepositCompletedEvent;
import com.stash.payments.transaction.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Handles Paystack {@code charge.success} events.
 *
 * <p>Flow:
 * <ol>
 *   <li>Look up the local {@code TransactionEntity} by the Paystack reference.</li>
 *   <li>Resolve the destination ledger account stored on the transaction row
 *       at deposit-initiation time (the USER_WALLET for a direct deposit, or
 *       a vault's ledger account for a vault deposit).</li>
 *   <li>Post a double-entry: CREDIT the destination account + DEBIT PAYSTACK_SETTLEMENT.</li>
 *   <li>Mark the transaction COMPLETED and publish {@link DepositCompletedEvent}.</li>
 * </ol>
 *
 * <p>All writes happen inside the caller's {@code @Transactional} context
 * (PaystackWebhookService). If any step throws, the whole transaction rolls back
 * and Paystack will retry the webhook delivery.
 */
@Component
public class ChargeSuccessHandler {

    private static final Logger log = LoggerFactory.getLogger(ChargeSuccessHandler.class);

    private final TransactionRepository  transactionRepository;
    private final LedgerAccountRepository ledgerAccountRepository;
    private final LedgerService          ledgerService;
    private final OutboxPublisher        outboxPublisher;
    private final Clock                  clock;
    private final UUID                   paystackSettlementAccountId;

    public ChargeSuccessHandler(TransactionRepository transactionRepository,
                                LedgerAccountRepository ledgerAccountRepository,
                                LedgerService ledgerService,
                                OutboxPublisher outboxPublisher,
                                Clock clock,
                                @Value("${stash.ledger.paystack-settlement-account-id}")
                                UUID paystackSettlementAccountId) {
        this.transactionRepository       = transactionRepository;
        this.ledgerAccountRepository     = ledgerAccountRepository;
        this.ledgerService               = ledgerService;
        this.outboxPublisher             = outboxPublisher;
        this.clock                       = clock;
        this.paystackSettlementAccountId = paystackSettlementAccountId;
    }

    /**
     * Processes a charge.success payload.
     *
     * @param data          the {@code data} object from the Paystack event
     * @param correlationId the webhook request correlation ID
     * @return the resulting {@code transaction.transactions.id},
     *         or {@code null} if the Paystack reference is not recognised
     */
    public UUID handle(Map<String, Object> data, String correlationId) {
        String paystackReference = (String) data.get("reference");
        long amountPesewas = toLong(data.get("amount"));

        TransactionEntity txn = transactionRepository
                .findByExternalReference(paystackReference)
                .orElse(null);

        if (txn == null) {
            log.warn("charge.success: no transaction found for Paystack ref={} — ignoring",
                    paystackReference);
            return null;
        }

        if (!"PENDING".equals(txn.getStatus())) {
            log.warn("charge.success: transaction {} is already {} — ignoring duplicate",
                    txn.getReference(), txn.getStatus());
            return txn.getId();
        }

        UUID userId = txn.getInitiatingUserId();
        UUID destinationAccountId = resolveDestinationAccount(txn, userId);

        LedgerWriteCommand command = new LedgerWriteCommand(
                "DEPOSIT",
                txn.getReference(),
                txn.getId(),
                "TRANSACTION",
                List.of(
                    EntryRequest.of(destinationAccountId, EntryDirection.CREDIT, amountPesewas),
                    EntryRequest.of(paystackSettlementAccountId, EntryDirection.DEBIT, amountPesewas)
                ),
                correlationId,
                "Paystack charge.success: " + paystackReference
        );

        LedgerWriteResult result = ledgerService.writeTransaction(command);
        Instant now = Instant.now(clock);

        txn.markCompleted(result.ledgerTransactionId(), now);
        transactionRepository.save(txn);

        outboxPublisher.publish(
                new DepositCompletedEvent(
                        txn.getId(),
                        result.ledgerTransactionId(),
                        userId,
                        destinationAccountId,
                        amountPesewas,
                        txn.getReference(),
                        correlationId
                ),
                correlationId
        );

        log.info("charge.success processed: paystackRef={} internalRef={} amount={}p userId={} " +
                "destinationAccount={} ledgerTxn={}",
                paystackReference, txn.getReference(), amountPesewas,
                userId, destinationAccountId, result.ledgerTransactionId());

        return txn.getId();
    }

    /**
     * Resolves the ledger account to credit. Uses {@code destination_ledger_account_id}
     * stored on the transaction row at deposit-initiation time (the USER_WALLET for a
     * direct deposit, or a vault's ledger account for a vault deposit). Falls back to
     * resolving USER_WALLET only for legacy rows created before this column existed —
     * this fallback should not be hit for any deposit initiated after V3.
     */
    private UUID resolveDestinationAccount(TransactionEntity txn, UUID userId) {
        UUID destination = txn.getDestinationLedgerAccountId();
        if (destination != null) {
            return destination;
        }

        log.warn("charge.success: destination_ledger_account_id is null for txn={} — " +
                "falling back to USER_WALLET. This indicates a pre-V3 PENDING row.",
                txn.getReference());

        return ledgerAccountRepository
                .findByOwnerTypeAndOwnerIdAndAccountType("USER", userId, "USER_WALLET")
                .orElseThrow(() -> new IllegalStateException(
                        "No USER_WALLET ledger account for userId=" + userId))
                .getId();
    }

    private static long toLong(Object value) {
        if (value instanceof Number n) return n.longValue();
        return Long.parseLong(String.valueOf(value));
    }
}
