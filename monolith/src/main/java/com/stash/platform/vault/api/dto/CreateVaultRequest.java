package com.stash.platform.vault.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record CreateVaultRequest(

        @NotBlank(message = "name is required")
        @Size(max = 100, message = "name must be 100 characters or fewer")
        String name,

        @NotBlank(message = "vault_type is required")
        @JsonProperty("vault_type")
        String vaultType,           // STANDARD or LOCKED

        // LOCKED vault unlock conditions (at least one required for LOCKED)
        @JsonProperty("unlock_at")
        Instant unlockAt,

        @Min(value = 1, message = "unlock_amount must be at least 1 pesewa")
        @JsonProperty("unlock_amount")
        Long unlockAmount,

        @JsonProperty("unlock_condition_logic")
        String unlockConditionLogic  // AND or OR; required if both conditions provided
) {}
