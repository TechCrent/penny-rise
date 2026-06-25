package com.stash.payments.ledger.service;

import com.stash.payments.ledger.api.dto.BalanceResponse;
import com.stash.payments.ledger.domain.LedgerAccountEntity;
import com.stash.payments.ledger.repository.LedgerAccountRepository;
import com.stash.payments.shared.security.CallerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Computes and returns account balances with ownership enforcement.
 *
 * <p><strong>Ownership rules:</strong>
 * <ul>
 *   <li><em>Internal callers</em> — skip all ownership checks; may query any account.</li>
 *   <li><em>USER_WALLET</em> — only the owning user (ledger_accounts.owner_id == userId).</li>
 *   <li><em>VAULT</em> — the monolith resolves vault → ledger account before calling;
 *       the Payments Service checks that owner_id is in the caller's owned-vault set.
 *       In v0.3 (no direct vault membership check here), the monolith is trusted —
 *       ownership is verified before the monolith calls this endpoint.
 *       User callers reaching here directly for a VAULT account get 404.</li>
 *   <li><em>SUSU_GROUP</em> — v0.4; stubbed as 404 for user callers in v0.3.</li>
 *   <li><em>SYSTEM</em> — 404 for all user callers; internal callers only.</li>
 * </ul>
 *
 * <p><strong>Why 404 and not 403?</strong> Returning 403 on unauthorized access
 * confirms the account exists, enabling account enumeration. 404 is always returned
 * for accounts the caller cannot see — whether it doesn't exist or isn't theirs.
 */
@Service
public class AccountBalanceService {

    private static final Logger log = LoggerFactory.getLogger(AccountBalanceService.class);

    // Account types a user caller may directly query (with ownership check)
    private static final Set<String> USER_QUERYABLE_TYPES = Set.of("USER_WALLET");

    // Account types where the monolith is trusted to have verified ownership
    // before calling (user callers still only reach these via the monolith proxy)
    private static final Set<String> MONOLITH_VERIFIED_TYPES = Set.of("VAULT", "SUSU_POT");

    private final LedgerAccountRepository ledgerAccountRepo;
    private final BalanceService          balanceService;
    private final Clock                   clock;

    public AccountBalanceService(LedgerAccountRepository ledgerAccountRepo,
                                 BalanceService balanceService,
                                 Clock clock) {
        this.ledgerAccountRepo = ledgerAccountRepo;
        this.balanceService    = balanceService;
        this.clock             = clock;
    }

    /**
     * Returns the balance for the given account, enforcing caller ownership.
     *
     * @param accountId     the ledger account UUID
     * @param callerContext identifies the caller (internal or user)
     * @return balance response
     * @throws ResponseStatusException 404 if account not found or caller lacks access
     */
    @Transactional(readOnly = true)
    public BalanceResponse getBalance(UUID accountId, CallerContext callerContext) {
        LedgerAccountEntity account = ledgerAccountRepo.findById(accountId)
                .orElseThrow(() -> notFound(accountId, callerContext, "not found"));

        if (!callerContext.isInternal()) {
            enforceOwnership(account, callerContext, accountId);
        }

        long balancePesewas = balanceService.computeBalanceFast(accountId);
        Instant asOf        = Instant.now(clock);

        log.debug("Balance query: account={} type={} balance={}p caller={}",
                accountId, account.getAccountType(), balancePesewas,
                callerContext.isInternal() ? "internal" : callerContext.userId());

        return BalanceResponse.of(
                accountId,
                account.getAccountType(),
                account.getStatus(),
                balancePesewas,
                asOf
        );
    }

    // ── Ownership enforcement ─────────────────────────────────────────────

    private void enforceOwnership(LedgerAccountEntity account,
                                   CallerContext caller, UUID accountId) {
        String type = account.getAccountType();

        // SYSTEM accounts: never visible to user callers
        if ("SYSTEM".equals(account.getOwnerType())
                || type.equals("PAYSTACK_SETTLEMENT")
                || type.equals("FEE_REVENUE")
                || type.equals("PENALTY_REVENUE")) {
            throw notFound(accountId, caller, "system account hidden from user callers");
        }

        // USER_WALLET: direct ownership check
        if ("USER_WALLET".equals(type)) {
            if (!account.getOwnerId().equals(caller.userId())) {
                throw notFound(accountId, caller, "USER_WALLET owner mismatch");
            }
            return;
        }

        // VAULT / SUSU_POT: in v0.3 the monolith proxies and verifies before calling.
        // Direct user-JWT calls to VAULT/SUSU_POT accounts return 404 — the mobile
        // app should never call this endpoint directly for non-wallet accounts.
        if (MONOLITH_VERIFIED_TYPES.contains(type)) {
            // If this endpoint was reached with a user JWT (not internal), it means
            // the mobile client called us directly, bypassing the monolith proxy.
            // Deny with 404 to prevent account enumeration.
            log.warn("User caller reached balance endpoint directly for {} account={} user={}",
                    type, accountId, caller.userId());
            throw notFound(accountId, caller, type + " requires monolith proxy in v0.3");
        }

        // Unknown account types: deny by default
        throw notFound(accountId, caller, "unknown account type: " + type);
    }

    private ResponseStatusException notFound(UUID accountId, CallerContext caller, String reason) {
        // Log at debug to avoid filling logs when clients probe — but record it
        log.debug("Balance 404: account={} caller={} reason={}",
                accountId,
                caller.isInternal() ? "internal" : String.valueOf(caller.userId()),
                reason);
        return new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Account not found: " + accountId);
    }
}
