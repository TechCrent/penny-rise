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
 * <p>On first call for a user, inserts the USER_WALLET ledger account row
 * (status=ACTIVE). Under Moolre, settlements use a single merchant account —
 * no per-user subaccount provisioning is required.
 *
 * <p>Idempotent: if the account already exists, returns it with no writes.
 */
@Service
public class UserWalletProvisioningService {

    private static final Logger log = LoggerFactory.getLogger(UserWalletProvisioningService.class);
    private static final String ACCOUNT_TYPE = "USER_WALLET";
    private static final String OWNER_TYPE   = "USER";

    private final LedgerAccountRepository accountRepository;
    private final Clock                   clock;

    public UserWalletProvisioningService(
            LedgerAccountRepository accountRepository,
            Clock clock) {
        this.accountRepository = accountRepository;
        this.clock             = clock;
    }

    /**
     * Provisions a USER_WALLET ledger account. Idempotent — safe to call multiple times.
     *
     * @param userId        the user's UUID from the monolith
     * @param userEmail     retained for API compatibility / logging
     * @param correlationId request trace ID
     * @return the newly created or pre-existing LedgerAccountEntity
     */
    @Transactional
    public LedgerAccountEntity provisionWallet(UUID userId, String userEmail,
                                               String correlationId) {
        Optional<LedgerAccountEntity> existingAccount = accountRepository
                .findByOwnerTypeAndOwnerIdAndAccountType(OWNER_TYPE, userId, ACCOUNT_TYPE);

        if (existingAccount.isPresent()) {
            log.info("USER_WALLET already exists for user={} — idempotent no-op. correlation={}",
                    userId, correlationId);
            return existingAccount.get();
        }

        LedgerAccountEntity account = new LedgerAccountEntity(
                ACCOUNT_TYPE, OWNER_TYPE, userId,
                "User wallet for user " + userId,
                Instant.now(clock)
        );
        LedgerAccountEntity saved = accountRepository.save(account);

        log.info("USER_WALLET provisioned: account={} user={} email={} correlation={}",
                saved.getId(), userId, userEmail, correlationId);

        return saved;
    }
}
