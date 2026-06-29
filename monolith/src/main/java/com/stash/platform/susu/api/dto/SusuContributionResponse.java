package com.stash.platform.susu.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.stash.platform.susu.domain.SusuContributionEntity;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

public record SusuContributionResponse(
        UUID                                                   id,
        @JsonProperty("susu_round_id")         UUID           susuRoundId,
        @JsonProperty("susu_group_id")         UUID           susuGroupId,
        @JsonProperty("member_user_id")        UUID           memberUserId,
        @JsonProperty("collected_amount")      long           collectedAmount,
        @JsonProperty("collected_amount_cedis") String        collectedAmountCedis,
        String                                                 status,
        @JsonProperty("transaction_reference") String         transactionReference,
        @JsonProperty("paid_at")               Instant        paidAt,
        @JsonProperty("round_fully_collected") boolean        roundFullyCollected
) {
    public static SusuContributionResponse from(SusuContributionEntity c,
                                                 boolean roundFullyCollected) {
        long collected = c.getCollectedAmount() != null ? c.getCollectedAmount() : 0L;
        String cedis = BigDecimal.valueOf(collected)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.FLOOR)
                .toPlainString();
        return new SusuContributionResponse(
                c.getId(), c.getSusuRoundId(), c.getSusuGroupId(),
                c.getMemberUserId(),
                collected,
                cedis,
                c.getStatus(),
                c.getTransactionReference(),
                c.getPaidAt(),
                roundFullyCollected
        );
    }
}
