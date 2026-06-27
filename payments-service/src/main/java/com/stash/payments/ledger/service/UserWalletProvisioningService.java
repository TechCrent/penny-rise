package com.stash.payments.ledger.service;

import com.stash.payments.ledger.domain.LedgerAccountEntity;
import com.stash.payments.ledger.repository.LedgerAccountRepository;
import com.stash.payments.outbox.service.OutboxPublisher;
import com.stash.payments.paystack.event.PaystackSubaccountProvisionRequestedEvent;
import com.stash.payments.paystack.repository.PaystackSubaccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Provisions USER_WALLET ledger accounts for new users.
 *
 * <p>On first call for a user:
 * <ol>
 *   <li>Inserts the USER_WALLET ledger account row (status=ACTIVE,
 *       external_reference=NULL initially).</li>
 *   <li>Writes a {@link PaystackSubaccountProvisionRequestedEvent} to the outbox
 *       in the same transaction.</li>
 * </ol>
 *
 * <p>The outbox relay then calls Paystack asynchronously, stores the subaccount
 * code in {@code paystack.paystack_sub_accounts}, and updates
 * {@code ledger_accounts.external_reference}. This two-phase design means:
 * <ul>
 *   <li>The ledger account is always committed even if Paystack is down.</li>
 *   <li>The Paystack call is retried up to 5 times via the relay.</li>
 *   <li>Dead-lettered events are visible to ops.</li>
 * </ul>
 *
 * <p>Idempotent: if the account already exists, returns it with no writes.
 */
@Service
public class UserWalletProvisioningService {

    private static final Logger log = LoggerFactory.getLogger(UserWalletProvisioningService.class);
    private static final String ACCOUNT_TYPE = "USER_WALLET";
    private static final String OWNER_TYPE   = "USER";

    private final LedgerAccountRepository      accountRepository;
    private final PaystackSubaccountRepository paystackSubaccountRepository;
    private final OutboxPublisher              outboxPublisher;
    private final Clock                        clock;

    public UserWalletProvisioningService(
            LedgerAccountRepository accountRepository,
            PaystackSubaccountRepository paystackSubaccountRepository,
            OutboxPublisher outboxPublisher,
            Clock clock) {
        this.accountRepository            = accountRepository;
        this.paystackSubaccountRepository = paystackSubaccountRepository;
        this.outboxPublisher              = outboxPublisher;
        this.clock                        = clock;
    }

    /**
     * Provisions a USER_WALLET ledger account and queues Paystack subaccount
     * creation via the outbox. Idempotent — safe to call multiple times.
     *
     * @param userId        the user's UUID from the monolith
     * @param userEmail     email address passed to Paystack as business_name context
     * @param correlationId request trace ID
     * @return the newly created or pre-existing LedgerAccountEntity
     */
    @Transactional
    public LedgerAccountEntity provisionWallet(UUID userId, String userEmail,
                                               String correlationId) {
        // ── Idempotency check 1: ledger account ───────────────────────────
        Optional<LedgerAccountEntity> existingAccount = accountRepository
                .findByOwnerTypeAndOwnerIdAndAccountType(OWNER_TYPE, userId, ACCOUNT_TYPE);

        if (existingAccount.isPresent()) {
            log.info("USER_WALLET already exists for user={} — idempotent no-op. correlation={}",
                    userId, correlationId);
            return existingAccount.get();
        }

        // ── Create ledger account ─────────────────────────────────────────
        LedgerAccountEntity account = new LedgerAccountEntity(
                ACCOUNT_TYPE, OWNER_TYPE, userId,
                "User wallet for user " + userId,
                Instant.now(clock)
        );
        LedgerAccountEntity saved = accountRepository.save(account);

        // ── Idempotency check 2: Paystack subaccount already provisioned?
        // (Handles the case where ledger account exists but subaccount does not —
        //  e.g. a previous relay attempt died after the account was created)
        boolean subaccountAlreadyExists = paystackSubaccountRepository
                .existsByOwnerTypeAndOwnerId(OWNER_TYPE, userId);

        if (!subaccountAlreadyExists) {
            // Write outbox event — committed in same transaction as account INSERT
            outboxPublisher.publish(
                    new PaystackSubaccountProvisionRequestedEvent(
                            saved.getId(), userId, userEmail, correlationId),
                    correlationId
            );
            log.info("USER_WALLET provisioned + subaccount provision queued: " +
                     "account={} user={} correlation={}",
                     saved.getId(), userId, correlationId);
        } else {
            log.info("USER_WALLET provisioned; Paystack subaccount already exists. " +
                     "Skipping outbox event. user={} correlation={}", userId, correlationId);
        }

        return saved;
    }
}
