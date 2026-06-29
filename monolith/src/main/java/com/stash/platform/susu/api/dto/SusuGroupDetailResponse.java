package com.stash.platform.susu.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Full group state for GET /api/v1/susu/groups/{id}.
 */
public record SusuGroupDetailResponse(
        UUID                                           id,
        String                                         name,
        String                                         status,
        @JsonProperty("organiser_user_id")     UUID    organiserUserId,
        @JsonProperty("is_caller_organiser")   boolean isCallerOrganiser,
        @JsonProperty("contribution_amount")   long    contributionAmount,
        @JsonProperty("contribution_amount_cedis") String contributionAmountCedis,
        String                                         frequency,
        @JsonProperty("target_member_count")   int     targetMemberCount,
        @JsonProperty("join_code")             String  joinCode,
        @JsonProperty("start_date")            String  startDate,
        @JsonProperty("created_at")            Instant createdAt,
        @JsonProperty("current_round")         CurrentRoundSummary currentRound,
        List<MemberSummary>                            members,
        @JsonProperty("caller_membership")     CallerMembership callerMembership
) {

    public record CurrentRoundSummary(
            UUID                                               id,
            @JsonProperty("round_number")          int         roundNumber,
            @JsonProperty("total_rounds")          int         totalRounds,
            String                                             status,
            @JsonProperty("recipient_user_id")     UUID        recipientUserId,
            @JsonProperty("recipient_display_name") String     recipientDisplayName,
            @JsonProperty("scheduled_collection_at") Instant   scheduledCollectionAt,
            @JsonProperty("expected_pot_amount")   Long        expectedPotAmount,
            @JsonProperty("expected_pot_amount_cedis") String  expectedPotAmountCedis,
            @JsonProperty("actual_pot_amount")     Long        actualPotAmount,
            List<ContributionStatus>                           contributions
    ) {}

    public record ContributionStatus(
            @JsonProperty("member_user_id")        UUID    memberUserId,
            @JsonProperty("display_name")          String  displayName,
            String                                         status,
            @JsonProperty("is_late")               boolean isLate,
            @JsonProperty("penalty_amount")        long    penaltyAmount,
            @JsonProperty("paid_at")               Instant paidAt
    ) {}

    public record MemberSummary(
            @JsonProperty("user_id")               UUID    userId,
            @JsonProperty("display_name")          String  displayName,
            @JsonProperty("rotation_position")     Integer rotationPosition,
            @JsonProperty("membership_status")     String  membershipStatus,
            @JsonProperty("joined_at")             Instant joinedAt,
            @JsonProperty("is_organiser")          boolean isOrganiser
    ) {}

    public record CallerMembership(
            UUID                                           id,
            @JsonProperty("rotation_position")     Integer rotationPosition,
            String                                         status,
            @JsonProperty("joined_at")             Instant joinedAt
    ) {}

    public static String toCedis(Long pesewas) {
        if (pesewas == null) return null;
        return BigDecimal.valueOf(pesewas)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.FLOOR)
                .toPlainString();
    }
}
