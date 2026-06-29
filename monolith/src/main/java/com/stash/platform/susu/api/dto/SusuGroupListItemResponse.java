package com.stash.platform.susu.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.stash.platform.susu.domain.SusuGroupEntity;
import com.stash.platform.susu.domain.SusuMembershipEntity;
import com.stash.platform.susu.domain.SusuRoundEntity;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

/**
 * One item in the caller's susu group list (GET /api/v1/susu/groups).
 */
public record SusuGroupListItemResponse(
        @JsonProperty("group_id")              UUID    groupId,
        String                                         name,
        String                                         status,
        @JsonProperty("organiser_user_id")     UUID    organiserUserId,
        @JsonProperty("is_organiser")          boolean isOrganiser,
        @JsonProperty("contribution_amount")   long    contributionAmount,
        @JsonProperty("contribution_amount_cedis") String contributionAmountCedis,
        String                                         frequency,
        @JsonProperty("target_member_count")   int     targetMemberCount,
        @JsonProperty("current_member_count")  long    currentMemberCount,
        @JsonProperty("current_round_number")  Integer currentRoundNumber,
        @JsonProperty("total_rounds")          Integer totalRounds,
        @JsonProperty("next_due_date")         Instant nextDueDate,
        @JsonProperty("caller_rotation_position") Integer callerRotationPosition,
        @JsonProperty("caller_is_next_recipient") boolean callerIsNextRecipient,
        @JsonProperty("join_code")             String  joinCode,
        @JsonProperty("created_at")            Instant createdAt
) {
    public static SusuGroupListItemResponse of(
            SusuGroupEntity group,
            SusuMembershipEntity callerMembership,
            long currentMemberCount,
            Integer totalRounds,
            SusuRoundEntity currentRound,
            UUID callerId) {

        boolean isOrganiser      = callerId.equals(group.getOrganiserUserId());
        Instant nextDueDate      = currentRound != null
                ? currentRound.getScheduledCollectionAt() : null;
        UUID    nextRecipientId  = currentRound != null
                ? currentRound.getRecipientUserId() : null;
        boolean isNextRecipient  = callerId.equals(nextRecipientId);

        String cedis = BigDecimal.valueOf(group.getContributionAmount())
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.FLOOR)
                .toPlainString();

        return new SusuGroupListItemResponse(
                group.getId(), group.getName(), group.getStatus(),
                group.getOrganiserUserId(), isOrganiser,
                group.getContributionAmount(), cedis,
                group.getFrequency(), group.getTargetMemberCount(),
                currentMemberCount, group.getCurrentRoundNumber(),
                totalRounds, nextDueDate,
                callerMembership.getRotationPosition(), isNextRecipient,
                group.getJoinCode(), group.getCreatedAt()
        );
    }
}
