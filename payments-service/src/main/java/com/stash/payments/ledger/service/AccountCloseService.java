package com.stash.payments.ledger.service;

import com.stash.payments.ledger.domain.LedgerAccountEntity;
import com.stash.payments.ledger.exception.AccountBalanceNotZeroException;
import com.stash.payments.ledger.repository.LedgerAccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/**
 * Closes ledger accounts for the monolith's account-deletion saga
 * (v0.5-019, step 3/4).
 *
 * <p>Idempotent: closing an already-{@code CLOSED} account is a no-op.
 * Rejects with {@link AccountBalanceNotZeroException} if the account still
 * holds a non-zero balance — the caller must sweep/withdraw funds first.
 */
@Service
public class AccountCloseService {

    private static final Logger log = LoggerFactory.getLogger(AccountCloseService.class);

    private final LedgerAccountRepository accountRepo;
    private final BalanceService balanceService;

    public AccountCloseService(LedgerAccountRepository accountRepo, BalanceService balanceService) {
        this.accountRepo = accountRepo;
        this.balanceService = balanceService;
    }

    @Transactional
    public void close(UUID accountId) {
        LedgerAccountEntity account = accountRepo.findById(accountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Ledger account not found: " + accountId));

        if ("CLOSED".equals(account.getStatus())) {
            log.info("AccountCloseService: account={} already CLOSED — no-op", accountId);
            return;
        }

        long balance = balanceService.computeBalanceFast(accountId);
        if (balance != 0) {
            throw new AccountBalanceNotZeroException(accountId, balance);
        }

        account.close();
        log.info("AccountCloseService: closed account={}", accountId);
    }
}
