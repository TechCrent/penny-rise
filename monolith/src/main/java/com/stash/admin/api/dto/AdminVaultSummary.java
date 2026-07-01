package com.stash.admin.api.dto;

import java.util.UUID;

public record AdminVaultSummary(
        UUID   id,
        String name,
        String vaultType,
        String status,
        long   balancePesewas
) {}
