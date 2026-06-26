package com.stash.platform.vault.service;

import com.stash.platform.vault.client.PaymentsBalanceClient;
import com.stash.platform.vault.domain.VaultEntity;
import com.stash.platform.vault.event.VaultUnlockedApplicationEvent;
import com.stash.platform.vault.repository.VaultRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Evaluates unlock conditions for one candidate vault and unlocks it if met.
 *
 * <p><strong>Unlock condition logic:</strong>
 * <ul>
 *   <li>Single date condition: unlock if {@code unlock_by_date <= now}.</li>
 *   <li>Single amount condition: unlock if {@code current_balance >= unlock_target_amount}.</li>
 *   <li>Both conditions with AND logic: both must be true simultaneously.</li>
 *   <li>Both conditions with OR logic: either suffices.</li>
 * </ul>
 *
 * <p><strong>No penalty:</strong> natural unlock carries no penalty.
 * The vault transitions to {@code status = ACTIVE}, {@code unlocked_at = now()}.
 * {@code vault_type} remains {@code LOCKED} (it is a permanent classification).
 * The user may then withdraw freely via the standard withdrawal endpoint.
 *
 * <p><strong>Idempotency:</strong> the query in {@code VaultAutoUnlockWorker} filters
 * on {@code unlocked_at IS NULL}, so already-unlocked vaults never appear as candidates.
 * If the vault is unlocked between the query and this processor running (extremely
 * unlikely), the status check at the top of this method will catch it.
 */
@Service
public class VaultAutoUnlockProcessor {

    private static final Logger log = LoggerFactory.getLogger(VaultAutoUnlockProcessor.class);

    private final VaultRepository          vaultRepo;
    private final PaymentsBalanceClient    balanceClient;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock                    clock;

    public VaultAutoUnlockProcessor(VaultRepository vaultRepo,
                                     PaymentsBalanceClient balanceClient,
                                     ApplicationEventPublisher eventPublisher,
                                     Clock clock) {
        this.vaultRepo      = vaultRepo;
        this.balanceClient  = balanceClient;
        this.eventPublisher = eventPublisher;
        this.clock          = clock;
    }

    /**
     * Evaluates and possibly unlocks a candidate vault.
     *
     * @param vault         the candidate vault (ACTIVE, LOCKED, unlocked_at IS NULL)
     * @param correlationId trace ID
     * @return true if the vault was unlocked
     */
    @Transactional
    public boolean evaluate(VaultEntity vault, String correlationId) {
        UUID vaultId = vault.getId();
        Instant now  = Instant.now(clock);

        // ── Guard: re-check status in case of race ────────────────────────
        if (!"ACTIVE".equals(vault.getStatus()) || vault.getUnlockedAt() != null) {
            log.debug("AutoUnlock: skipping vault={} — already processed or wrong status",
                    vaultId);
            return false;
        }

        // ── Evaluate conditions ───────────────────────────────────────────
        boolean dateMet   = evaluateDateCondition(vault, now);
        boolean amountMet = evaluateAmountCondition(vault, correlationId);

        boolean shouldUnlock = determineUnlock(vault, dateMet, amountMet, now);

        if (!shouldUnlock) {
            log.debug("AutoUnlock: conditions not met for vault={} dateMet={} amountMet={} " +
                      "logic={}", vaultId, dateMet, amountMet, vault.getUnlockConditionLogic());
            return false;
        }

        // ── Unlock the vault ──────────────────────────────────────────────
        setUnlocked(vault, now);
        vaultRepo.save(vault);

        // Publish AFTER_COMMIT via Spring application event
        eventPublisher.publishEvent(
                new VaultUnlockedApplicationEvent(this, vaultId, vault.getOwnerUserId(),
                        now, correlationId));

        log.info("AutoUnlock: vault={} unlocked at={} dateMet={} amountMet={} correlation={}",
                vaultId, now, dateMet, amountMet, correlationId);
        return true;
    }

    // ── Condition evaluation ──────────────────────────────────────────────

    private boolean evaluateDateCondition(VaultEntity vault, Instant now) {
        if (vault.getUnlockByDate() == null) return false;
        return !vault.getUnlockByDate().isAfter(now);
    }

    private boolean evaluateAmountCondition(VaultEntity vault, String correlationId) {
        if (vault.getUnlockTargetAmount() == null) return false;

        Optional<Long> balance = balanceClient.fetchBalance(
                vault.getLedgerAccountId(), correlationId);

        if (balance.isEmpty()) {
            log.warn("AutoUnlock: could not fetch balance for vault={} — " +
                     "amount condition cannot be evaluated. correlation={}",
                    vault.getId(), correlationId);
            return false;  // conservative: don't unlock if we can't verify balance
        }

        boolean met = balance.get() >= vault.getUnlockTargetAmount();
        log.debug("AutoUnlock: amount check vault={} balance={}p target={}p met={}",
                vault.getId(), balance.get(), vault.getUnlockTargetAmount(), met);
        return met;
    }

    private boolean determineUnlock(VaultEntity vault, boolean dateMet,
                                     boolean amountMet, Instant now) {
        boolean hasDate   = vault.getUnlockByDate() != null;
        boolean hasAmount = vault.getUnlockTargetAmount() != null;

        if (hasDate && !hasAmount) {
            // Only date condition
            return dateMet;
        }
        if (hasAmount && !hasDate) {
            // Only amount condition
            return amountMet;
        }
        // Both conditions — apply unlock_condition_logic
        String logic = vault.getUnlockConditionLogic();
        if ("OR".equals(logic)) {
            return dateMet || amountMet;
        }
        // Default to AND (also the value when logic = 'AND' or null)
        return dateMet && amountMet;
    }

    private void setUnlocked(VaultEntity vault, Instant now) {
        try {
            var statusField = VaultEntity.class.getDeclaredField("status");
            statusField.setAccessible(true);
            statusField.set(vault, "ACTIVE");

            var unlockedAtField = VaultEntity.class.getDeclaredField("unlockedAt");
            unlockedAtField.setAccessible(true);
            unlockedAtField.set(vault, now);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to set vault unlocked state", e);
        }
    }
}
