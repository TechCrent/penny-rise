package com.stash.platform.susu.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record SusuActivationResponse(
        UUID                                               id,
        String                                             name,
        String                                             status,
        @JsonProperty("start_date")       LocalDate        startDate,
        @JsonProperty("current_round_number") int          currentRoundNumber,
        @JsonProperty("ledger_account_id") UUID           ledgerAccountId,
        @JsonProperty("contribution_amount") long         contributionAmount,
        @JsonProperty("contribution_amount_cedis") String contributionAmountCedis,
        String                                             frequency,
        @JsonProperty("target_member_count") int          targetMemberCount,
        @JsonProperty("members")           List<MemberInfo> members,
        @JsonProperty("round_1")           RoundInfo       round1,
        @JsonProperty("activated_at")      Instant         activatedAt
) {

    public record MemberInfo(
            @JsonProperty("user_id")           UUID    userId,
            @JsonProperty("rotation_position") int     rotationPosition,
            @JsonProperty("joined_at")         Instant joinedAt
    ) {}

    public record RoundInfo(
            UUID                                            id,
            @JsonProperty("round_number")       int         roundNumber,
            String                                          status,
            @JsonProperty("recipient_user_id")  UUID        recipientUserId,
            @JsonProperty("scheduled_collection_at") Instant scheduledCollectionAt,
            @JsonProperty("expected_pot_amount") long        expectedPotAmount,
            @JsonProperty("expected_pot_amount_cedis") String expectedPotAmountCedis,
            @JsonProperty("contribution_count") int          contributionCount
    ) {}

    public static String toCedis(long pesewas) {
        return BigDecimal.valueOf(pesewas)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.FLOOR)
                .toPlainString();
    }
}
