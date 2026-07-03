package com.stash.platform.subscription.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record DowngradePreviewResponse(
        @JsonProperty("vaults_to_be_frozen") List<FrozenVaultPreview> vaultsToBeFrozen,
        @JsonProperty("susu_groups_to_be_frozen") List<FrozenSusuGroupPreview> susuGroupsToBeFrozen,
        boolean committed
) {}
