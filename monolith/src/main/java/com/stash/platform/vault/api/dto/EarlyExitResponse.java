package com.stash.platform.vault.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.stash.platform.vault.domain.EarlyExitRequestEntity;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

public record EarlyExitResponse(
        UUID                                         id,
        @JsonProperty("vault_id")              UUID   vaultId,
        String                                         reason,
        @JsonProperty("balance_at_request_pesewas") long  balanceAtRequestPesewas,
        @JsonProperty("balance_at_request_cedis")   String balanceAtRequestCedis,
        @JsonProperty("penalty_amount_pesewas") long   penaltyAmountPesewas,
        @JsonProperty("penalty_amount_cedis")   String penaltyAmountCedis,
        @JsonProperty("release_amount_pesewas") long   releaseAmountPesewas,
        @JsonProperty("release_amount_cedis")   String releaseAmountCedis,
        @JsonProperty("scheduled_release_at")   Instant scheduledReleaseAt,
        String                                         status
) {
    public static EarlyExitResponse from(EarlyExitRequestEntity r) {
        return new EarlyExitResponse(
                r.getId(), r.getVaultId(), r.getReason(),
                r.getBalanceAtRequest(), toCedis(r.getBalanceAtRequest()),
                r.getPenaltyAmount(),   toCedis(r.getPenaltyAmount()),
                r.getReleaseAmount(),   toCedis(r.getReleaseAmount()),
                r.getScheduledReleaseAt(), r.getStatus()
        );
    }

    private static String toCedis(long pesewas) {
        return BigDecimal.valueOf(pesewas)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.FLOOR)
                .toPlainString();
    }
}
