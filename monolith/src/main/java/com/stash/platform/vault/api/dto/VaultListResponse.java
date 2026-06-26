package com.stash.platform.vault.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record VaultListResponse(
        List<VaultListItemResponse>                        vaults,
        @JsonProperty("total_count") int                   totalCount,
        @JsonProperty("balance_unavailable_count") int     balanceUnavailableCount
) {
    /**
     * @param balanceUnavailableCount number of vaults whose balance could not be fetched.
     *                                Non-zero indicates a partial Payments Service outage.
     */
    public static VaultListResponse of(List<VaultListItemResponse> vaults,
                                       int balanceUnavailableCount) {
        return new VaultListResponse(vaults, vaults.size(), balanceUnavailableCount);
    }
}
