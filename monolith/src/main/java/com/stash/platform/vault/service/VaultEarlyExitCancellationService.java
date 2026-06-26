package com.stash.platform.vault.service;

import com.stash.platform.vault.domain.EarlyExitRequestEntity;
import com.stash.platform.vault.domain.VaultEntity;
import com.stash.platform.vault.repository.EarlyExitRequestRepository;
import com.stash.platform.vault.repository.VaultRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Cancels a pending locked-vault early-exit request.
 *
 * <p><strong>What happens on cancellation:</strong>
 * <ol>
 *   <li>The {@code locked_vault_early_exit_requests} row is transitioned to
 *       {@code CANCELLED} with {@code resolved_at = now()}.</li>
 *   <li>The vault status is transitioned back to {@code ACTIVE} and
 *       {@code early_exit_in_progress} is set to {@code false}.</li>
 *   <li>Both changes commit in the same {@code @Transactional} boundary —
 *       atomically. A crash between the two updates is impossible at the
 *       DB level.</li>
 * </ol>
 *
 * <p><strong>What is NOT reversed:</strong> no ledger entries were written
 * during the early-exit request (the Payments Service is not involved until
 * the release worker fires). Cancellation therefore has no financial side
 * effects — no reversal transaction is needed.
 *
 * <p><strong>Auto-release worker safety:</strong> the release worker
 * (v0.3-033) queries for PENDING requests with
 * {@code scheduled_release_at <= now()}. A CANCELLED request has
 * {@code status = CANCELLED} and will never appear in that query.
 * The partial unique index and the status check together ensure the worker
 * and this cancellation endpoint cannot both act on the same request.
 */
@Service
public class VaultEarlyExitCancellationService {

    private static final Logger log =
            LoggerFactory.getLogger(VaultEarlyExitCancellationService.class);

    private final VaultRepository            vaultRepo;
    private final EarlyExitRequestRepository requestRepo;
    private final Clock                      clock;

    public VaultEarlyExitCancellationService(VaultRepository vaultRepo,
                                              EarlyExitRequestRepository requestRepo,
                                              Clock clock) {
        this.vaultRepo   = vaultRepo;
        this.requestRepo = requestRepo;
        this.clock       = clock;
    }

    /**
     * Cancels the pending early-exit request for the given vault.
     *
     * @param vaultId       the vault whose early exit to cancel
     * @param userId        the authenticated user (must own the vault)
     * @param correlationId trace ID
     * @throws ResponseStatusException 404 if vault not found;
     *                                 403 if not owned by user;
     *                                 404 if no PENDING early-exit request exists
     *                                     (code = VAULT_NO_PENDING_EARLY_EXIT)
     */
    @Transactional
    public void cancelEarlyExit(UUID vaultId, UUID userId, String correlationId) {
        // ── Load and validate vault ───────────────────────────────────────
        VaultEntity vault = vaultRepo.findById(vaultId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Vault not found: " + vaultId));

        if (!userId.equals(vault.getOwnerUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Vault does not belong to the authenticated user.");
        }

        // ── Find PENDING early-exit request ───────────────────────────────
        // If the status is COMPLETED (release worker already ran) or CANCELLED
        // (already cancelled), findPendingByVaultId returns empty → 404.
        // If the vault is ACTIVE (no early exit in progress), same result.
        EarlyExitRequestEntity request = requestRepo.findPendingByVaultId(vaultId)
                .orElseThrow(() -> {
                    log.debug("Cancel early-exit: no PENDING request for vault={} user={} " +
                              "correlation={}", vaultId, userId, correlationId);
                    return new ResponseStatusException(HttpStatus.NOT_FOUND,
                            "VAULT_NO_PENDING_EARLY_EXIT: No pending early-exit request " +
                            "exists for this vault. It may have already been processed " +
                            "or cancelled.");
                });

        // ── Cancel the request ────────────────────────────────────────────
        Instant now = Instant.now(clock);
        request.markCancelled(now);
        requestRepo.save(request);

        // ── Restore vault to ACTIVE ───────────────────────────────────────
        setVaultActive(vault);
        vaultRepo.save(vault);

        log.info("Early-exit cancelled: vault={} requestId={} user={} correlation={}",
                vaultId, request.getId(), userId, correlationId);
    }

    private void setVaultActive(VaultEntity vault) {
        try {
            var statusField = VaultEntity.class.getDeclaredField("status");
            statusField.setAccessible(true);
            statusField.set(vault, "ACTIVE");
            var exitField = VaultEntity.class.getDeclaredField("earlyExitInProgress");
            exitField.setAccessible(true);
            exitField.set(vault, false);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to restore vault to ACTIVE status", e);
        }
    }
}
