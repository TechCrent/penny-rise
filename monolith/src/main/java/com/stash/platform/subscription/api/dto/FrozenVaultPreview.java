package com.stash.platform.subscription.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record FrozenVaultPreview(
        @JsonProperty("vault_id") String vaultId,
        @JsonProperty("vault_name") String vaultName,
        @JsonProperty("vault_type") String vaultType
) {}
