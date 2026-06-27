package com.stash.payments.ledger.api;

import com.stash.payments.ledger.domain.LedgerAccountEntity;
import com.stash.payments.ledger.repository.LedgerAccountRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Internal endpoint for provisioning ledger accounts.
 * Called by the monolith when creating vaults, susu groups, and other
 * entities that need a ledger account.
 *
 * <p>Protected by {@link com.stash.payments.shared.security.InternalServiceAuthFilter}.
 * Idempotent: if an account already exists for the (owner_type, owner_id, account_type)
 * combination, the existing account is returned rather than creating a duplicate.
 */
@RestController
@RequestMapping("/internal/v1/ledger")
public class LedgerAccountProvisionController {

    private static final Logger log =
            LoggerFactory.getLogger(LedgerAccountProvisionController.class);

    private final LedgerAccountRepository accountRepository;
    private final Clock                   clock;

    public LedgerAccountProvisionController(LedgerAccountRepository accountRepository,
                                             Clock clock) {
        this.accountRepository = accountRepository;
        this.clock             = clock;
    }

    record ProvisionRequest(
            @NotBlank String account_type,
            @NotBlank String owner_type,
            @NotNull  UUID   owner_id,
            String           description
    ) {}

    record ProvisionResponse(String ledger_account_id, String account_type,
                              String owner_type, String status, boolean created) {}

    @PostMapping("/accounts")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public ProvisionResponse provision(@Valid @RequestBody ProvisionRequest request) {
        // Idempotency: return existing if already provisioned
        Optional<LedgerAccountEntity> existing = accountRepository
                .findByOwnerTypeAndOwnerIdAndAccountType(
                        request.owner_type(), request.owner_id(), request.account_type());

        if (existing.isPresent()) {
            LedgerAccountEntity acc = existing.get();
            log.info("LedgerAccountProvisionController: idempotent — returning existing " +
                     "account={} type={} owner={}:{}", acc.getId(),
                     acc.getAccountType(), acc.getOwnerType(), acc.getOwnerId());
            return new ProvisionResponse(
                    acc.getId().toString(), acc.getAccountType(),
                    acc.getOwnerType(), acc.getStatus(), false);
        }

        LedgerAccountEntity account = new LedgerAccountEntity(
                request.account_type(), request.owner_type(),
                request.owner_id(), request.description(), Instant.now(clock));
        LedgerAccountEntity saved = accountRepository.save(account);

        log.info("LedgerAccountProvisionController: provisioned account={} type={} owner={}:{}",
                saved.getId(), saved.getAccountType(), saved.getOwnerType(), saved.getOwnerId());

        return new ProvisionResponse(
                saved.getId().toString(), saved.getAccountType(),
                saved.getOwnerType(), saved.getStatus(), true);
    }
}
