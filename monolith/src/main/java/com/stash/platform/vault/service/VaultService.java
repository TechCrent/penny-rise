package com.stash.platform.vault.service;

import com.stash.platform.vault.domain.VaultEntity;
import com.stash.platform.vault.repository.VaultRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Admin-facing vault query service. Returns raw vault entities so that callers
 * can project only the fields they need without coupling the vault module to
 * any admin-specific DTO.
 */
@Service
public class VaultService {

    private final VaultRepository vaultRepository;

    public VaultService(VaultRepository vaultRepository) {
        this.vaultRepository = vaultRepository;
    }

    public List<VaultEntity> listForOwner(UUID userId) {
        return vaultRepository.findByOwnerUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(userId);
    }

    /**
     * Returns the ledger account IDs of all non-deleted vaults owned by the user.
     * Used by the deletion cleanup saga to close each vault's ledger account in
     * Payments Service before the user record is fully removed.
     */
    public List<UUID> listActiveLedgerAccountIdsForOwner(UUID userId) {
        return listForOwner(userId).stream()
                .map(VaultEntity::getLedgerAccountId)
                .toList();
    }

    /**
     * Returns the user-facing name of a vault by its ID.
     * Used by TransactionHistoryEnricher (v0.5-020) to populate account_name
     * for VAULT_DEPOSIT / VAULT_WITHDRAWAL transaction rows.
     */
    public Optional<String> getVaultName(UUID vaultId) {
        return vaultRepository.findById(vaultId).map(VaultEntity::getName);
    }
}
