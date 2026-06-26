package com.stash.platform.vault.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.stash.platform.vault.domain.VaultEntity;

import java.time.Instant;
import java.util.UUID;

public record VaultResponse(
        UUID                                   id,
        @JsonProperty("owner_user_id")  UUID   ownerUserId,
        String                                 name,
        @JsonProperty("vault_type")     String vaultType,
        String                                 status,
        @JsonProperty("ledger_account_id") UUID ledgerAccountId,
        @JsonProperty("unlock_at")      Instant unlockAt,
        @JsonProperty("unlock_amount")  Long    unlockAmount,
        @JsonProperty("unlock_condition_logic") String unlockConditionLogic,
        @JsonProperty("created_at")     Instant createdAt
) {
    public static VaultResponse from(VaultEntity v) {
        return new VaultResponse(
                v.getId(), v.getOwnerUserId(), v.getName(),
                v.getVaultType(), v.getStatus(), v.getLedgerAccountId(),
                v.getUnlockByDate(), v.getUnlockTargetAmount(),
                v.getUnlockConditionLogic(), v.getCreatedAt());
    }
}
