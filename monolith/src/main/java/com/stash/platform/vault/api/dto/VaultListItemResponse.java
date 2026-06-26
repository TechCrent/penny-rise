package com.stash.platform.vault.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.stash.platform.vault.domain.VaultEntity;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

/**
 * One vault item in the list response, enriched with balance.
 *
 * <p>{@code balance_pesewas} and {@code balance_cedis} are null when the
 * Payments Service was unavailable for this vault. The mobile client must
 * handle null gracefully (display a placeholder or "balance unavailable").
 */
public record VaultListItemResponse(
        UUID                                       id,
        String                                     name,
        @JsonProperty("vault_type")    String      vaultType,
        String                                     status,
        @JsonProperty("ledger_account_id") UUID    ledgerAccountId,
        @JsonProperty("balance_pesewas") Long      balancePesewas,
        @JsonProperty("balance_cedis")   String    balanceCedis,
        @JsonProperty("unlock_at")       Instant   unlockAt,
        @JsonProperty("unlock_amount")   Long      unlockAmount,
        @JsonProperty("unlock_condition_logic") String unlockConditionLogic,
        @JsonProperty("early_exit_in_progress") boolean earlyExitInProgress,
        @JsonProperty("created_at")      Instant   createdAt
) {
    /**
     * Creates a list item from a vault entity with optional balance enrichment.
     * Pass null for balancePesewas when the Payments Service was unavailable.
     */
    public static VaultListItemResponse from(VaultEntity v, Long balancePesewas) {
        String balanceCedis = null;
        if (balancePesewas != null) {
            balanceCedis = BigDecimal.valueOf(balancePesewas)
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.FLOOR)
                    .toPlainString();
        }
        return new VaultListItemResponse(
                v.getId(), v.getName(), v.getVaultType(), v.getStatus(),
                v.getLedgerAccountId(),
                balancePesewas, balanceCedis,
                v.getUnlockByDate(), v.getUnlockTargetAmount(),
                v.getUnlockConditionLogic(), v.isEarlyExitInProgress(),
                v.getCreatedAt()
        );
    }
}
