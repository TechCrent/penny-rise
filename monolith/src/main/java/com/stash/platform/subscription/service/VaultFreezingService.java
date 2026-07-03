package com.stash.platform.subscription.service;

import com.stash.platform.subscription.api.dto.FrozenVaultPreview;
import com.stash.platform.vault.domain.VaultEntity;
import com.stash.platform.vault.repository.VaultRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Computes and applies vault freezing on subscription downgrade.
 *
 * <p>Oldest-first: a user's newest vaults stay active, the longest-running
 * excess ones freeze (VaultRepository.findActiveByOwnerUserIdAndVaultTypeOrderByCreatedAtAsc).
 * Only ACTIVE vaults are candidates — CLOSED/EARLY_EXIT_PENDING/already-FROZEN
 * vaults are left alone.
 */
@Service
public class VaultFreezingService {

    private final VaultRepository vaultRepository;

    public VaultFreezingService(VaultRepository vaultRepository) {
        this.vaultRepository = vaultRepository;
    }

    /** Read-only — computes what WOULD be frozen, commits nothing. */
    public List<FrozenVaultPreview> previewExcessVaults(UUID userId, int standardLimit, int lockedLimit) {
        List<VaultEntity> excess = excessVaults(userId, standardLimit, lockedLimit);
        return excess.stream()
                .map(v -> new FrozenVaultPreview(v.getId().toString(), v.getName(), v.getVaultType()))
                .toList();
    }

    /** Actually flips status — called only from the downgrade commit path. */
    public void freezeExcessVaults(UUID userId, int standardLimit, int lockedLimit) {
        for (VaultEntity vault : excessVaults(userId, standardLimit, lockedLimit)) {
            vaultRepository.freezeIfActive(vault.getId());
        }
    }

    private List<VaultEntity> excessVaults(UUID userId, int standardLimit, int lockedLimit) {
        List<VaultEntity> standardExcess = excessOldestFirst(
                vaultRepository.findActiveByOwnerUserIdAndVaultTypeOrderByCreatedAtAsc(userId, "STANDARD"),
                standardLimit);
        List<VaultEntity> lockedExcess = excessOldestFirst(
                vaultRepository.findActiveByOwnerUserIdAndVaultTypeOrderByCreatedAtAsc(userId, "LOCKED"),
                lockedLimit);

        return java.util.stream.Stream.concat(standardExcess.stream(), lockedExcess.stream()).toList();
    }

    /** activeVaults is already oldest-first — the excess ones beyond the limit are the ones that freeze. */
    private List<VaultEntity> excessOldestFirst(List<VaultEntity> activeVaultsOldestFirst, int limit) {
        if (activeVaultsOldestFirst.size() <= limit) {
            return List.of();
        }
        int excessCount = activeVaultsOldestFirst.size() - limit;
        return activeVaultsOldestFirst.subList(0, excessCount);
    }
}
