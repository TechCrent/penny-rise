package com.stash.payments.ledger.service;

import com.stash.payments.ledger.domain.LedgerAccountEntity;
import com.stash.payments.ledger.repository.LedgerAccountRepository;
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
 * <p>Idempotent by design: if the account already exists, the method
 * returns the existing account without creating a duplicate. The caller
 * (UserCreatedEventConsumer) acknowledges the RabbitMQ message either way —
 * no requeue loop occurs.
 */
@Service
public class UserWalletProvisioningService {

    private static final Logger log =
            LoggerFactory.getLogger(UserWalletProvisioningService.class);

    private static final String ACCOUNT_TYPE = "USER_WALLET";
    private static final String OWNER_TYPE   = "USER";

    private final LedgerAccountRepository accountRepository;
    private final Clock clock;

    public UserWalletProvisioningService(LedgerAccountRepository accountRepository,
                                         Clock clock) {
        this.accountRepository = accountRepository;
        this.clock             = clock;
    }

    /**
     * Provisions a USER_WALLET ledger account for the given user.
     * If the account already exists, returns it unchanged.
     *
     * @param userId        the user's UUID from the monolith
     * @param correlationId request trace ID carried from the signup event
     * @return the newly created or pre-existing LedgerAccountEntity
     */
    @Transactional
    public LedgerAccountEntity provisionWallet(UUID userId, String correlationId) {
        Optional<LedgerAccountEntity> existing = accountRepository
                .findByOwnerTypeAndOwnerIdAndAccountType(OWNER_TYPE, userId, ACCOUNT_TYPE);

        if (existing.isPresent()) {
            log.info("USER_WALLET already exists for user={} — idempotent no-op. " +
                     "correlation={}", userId, correlationId);
            return existing.get();
        }

        LedgerAccountEntity account = new LedgerAccountEntity(
                ACCOUNT_TYPE,
                OWNER_TYPE,
                userId,
                "User wallet for user " + userId,
                Instant.now(clock)
        );
        LedgerAccountEntity saved = accountRepository.save(account);

        log.info("USER_WALLET provisioned: account={} user={} correlation={}",
                saved.getId(), userId, correlationId);

        return saved;
    }
}
