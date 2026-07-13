package com.stash.payments.webhook.service;

import com.stash.payments.ledger.domain.EntryDirection;
import com.stash.payments.ledger.domain.EntryRequest;
import com.stash.payments.ledger.service.LedgerWriteCommand;
import com.stash.payments.ledger.service.LedgerWriteResult;
import com.stash.payments.ledger.service.LedgerService;
import com.stash.payments.ledger.repository.LedgerAccountRepository;
import com.stash.payments.moolre.amount.MoolreAmountConverter;
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
 * Handles successful MoMo collection events (Moolre webhook / reconciliation).
 *
 * <p>Flow:
 * <ol>
 *   <li>Look up the local {@code TransactionEntity} by our STSH reference
 *       ({@code data.externalref}), falling back to {@code external_reference}.</li>
 *   <li>Resolve the destination ledger account stored on the transaction row
 *       at deposit-initiation time.</li>
 *   <li>Post a double-entry: CREDIT the destination account + DEBIT MOOLRE_SETTLEMENT.</li>
 *   <li>Mark the transaction COMPLETED and publish {@link DepositCompletedEvent}.</li>
 * </ol>
 */
@Component
public class ChargeSuccessHandler {

    private static final Logger log = LoggerFactory.getLogger(ChargeSuccessHandler.class);

    private final TransactionRepository   transactionRepository;
    private final LedgerAccountRepository ledgerAccountRepository;
    private final LedgerService           ledgerService;
    private final OutboxPublisher         outboxPublisher;
    private final Clock                   clock;
    private final UUID                    moolreSettlementAccountId;

    public ChargeSuccessHandler(TransactionRepository transactionRepository,
                                LedgerAccountRepository ledgerAccountRepository,
                                LedgerService ledgerService,
                                OutboxPublisher outboxPublisher,
                                Clock clock,
                                @Value("${stash.ledger.moolre-settlement-account-id:00000000-0000-0000-0000-000000000004}")
                                UUID moolreSettlementAccountId) {
        this.transactionRepository     = transactionRepository;
        this.ledgerAccountRepository   = ledgerAccountRepository;
        this.ledgerService             = ledgerService;
        this.outboxPublisher           = outboxPublisher;
        this.clock                     = clock;
        this.moolreSettlementAccountId = moolreSettlementAccountId;
    }

    /**
     * Processes a successful charge/collection payload.
     *
     * @param data          the {@code data} object from the provider event
     *                      (expects {@code externalref} or {@code reference}, and {@code amount})
     * @param correlationId the webhook request correlation ID
     * @return the resulting {@code transaction.transactions.id},
     *         or {@code null} if the reference is not recognised
     */
    public UUID handle(Map<String, Object> data, String correlationId) {
        String lookupKey = firstNonBlank(
                stringVal(data.get("externalref")),
                stringVal(data.get("reference")));
        long amountPesewas = parseAmountToPesewas(data.get("amount"));

        if (lookupKey == null) {
            log.warn("charge.success: missing externalref/reference — ignoring");
            return null;
        }

        TransactionEntity txn = transactionRepository
                .findByReference(lookupKey)
                .or(() -> transactionRepository.findByExternalReference(lookupKey))
                .orElse(null);

        if (txn == null) {
            log.warn("charge.success: no transaction found for ref={} — ignoring", lookupKey);
            return null;
        }

        if (!"PENDING".equals(txn.getStatus())) {
            log.warn("charge.success: transaction {} is already {} — ignoring duplicate",
                    txn.getReference(), txn.getStatus());
            return txn.getId();
        }

        UUID userId = txn.getInitiatingUserId();
        UUID destinationAccountId = resolveDestinationAccount(txn, userId);
        String businessRefType = resolveDepositBusinessReferenceType(destinationAccountId);

        LedgerWriteCommand command = new LedgerWriteCommand(
                "DEPOSIT",
                txn.getReference(),
                txn.getId(),
                businessRefType,
                List.of(
                    EntryRequest.of(destinationAccountId, EntryDirection.CREDIT, amountPesewas),
                    EntryRequest.of(moolreSettlementAccountId, EntryDirection.DEBIT, amountPesewas)
                ),
                correlationId,
                "Moolre charge.success: " + lookupKey
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

        log.info("charge.success processed: lookupKey={} internalRef={} amount={}p userId={} " +
                "destinationAccount={} ledgerTxn={}",
                lookupKey, txn.getReference(), amountPesewas,
                userId, destinationAccountId, result.ledgerTransactionId());

        return txn.getId();
    }

    private String resolveDepositBusinessReferenceType(UUID destinationAccountId) {
        return ledgerAccountRepository.findById(destinationAccountId)
                .map(account -> "USER_WALLET".equals(account.getAccountType())
                        ? "WALLET_DEPOSIT"
                        : "VAULT_DEPOSIT")
                .orElse("VAULT_DEPOSIT");
    }

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

    /**
     * Moolre amounts arrive as GHS strings (e.g. {@code "20.00"}); legacy Paystack
     * payloads used pesewas as a number. Accept both.
     */
    static long parseAmountToPesewas(Object value) {
        if (value == null) {
            throw new IllegalArgumentException("amount is required");
        }
        if (value instanceof Number n) {
            // Heuristic: fractional → GHS; integer → already pesewas (Paystack-style)
            if (value instanceof Double || value instanceof Float
                    || (n.doubleValue() != Math.rint(n.doubleValue()))) {
                return MoolreAmountConverter.ghsStringToPesewas(String.valueOf(n));
            }
            return n.longValue();
        }
        String raw = String.valueOf(value).trim();
        if (raw.contains(".")) {
            return MoolreAmountConverter.ghsStringToPesewas(raw);
        }
        return Long.parseLong(raw);
    }

    private static String stringVal(Object value) {
        if (value == null) return null;
        String s = String.valueOf(value).trim();
        return s.isEmpty() || "null".equalsIgnoreCase(s) ? null : s;
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) return a;
        if (b != null && !b.isBlank()) return b;
        return null;
    }
}
