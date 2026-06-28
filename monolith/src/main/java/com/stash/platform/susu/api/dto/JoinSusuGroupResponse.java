package com.stash.platform.susu.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.stash.platform.susu.domain.SusuGroupEntity;
import com.stash.platform.susu.domain.SusuMembershipEntity;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

/**
 * Response returned when a user successfully joins a susu group.
 * Contains the group details (so the mobile screen can render the group card
 * immediately) and the caller's membership row.
 */
public record JoinSusuGroupResponse(
        SusuGroupSummary group,
        MembershipSummary membership
) {
    public static JoinSusuGroupResponse from(SusuGroupEntity g, SusuMembershipEntity m,
                                              long currentMemberCount) {
        return new JoinSusuGroupResponse(
                SusuGroupSummary.from(g, currentMemberCount),
                MembershipSummary.from(m)
        );
    }

    public record SusuGroupSummary(
            UUID                                           id,
            String                                         name,
            @JsonProperty("organiser_user_id") UUID        organiserUserId,
            @JsonProperty("contribution_amount") long      contributionAmount,
            @JsonProperty("contribution_amount_cedis") String contributionAmountCedis,
            String                                         frequency,
            @JsonProperty("target_member_count") int       targetMemberCount,
            @JsonProperty("current_member_count") long     currentMemberCount,
            String                                         status,
            @JsonProperty("join_code") String              joinCode,
            @JsonProperty("created_at") Instant            createdAt
    ) {
        static SusuGroupSummary from(SusuGroupEntity g, long memberCount) {
            String cedis = BigDecimal.valueOf(g.getContributionAmount())
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.FLOOR)
                    .toPlainString();
            return new SusuGroupSummary(
                    g.getId(), g.getName(), g.getOrganiserUserId(),
                    g.getContributionAmount(), cedis,
                    g.getFrequency(), g.getTargetMemberCount(), memberCount,
                    g.getStatus(), g.getJoinCode(), g.getCreatedAt()
            );
        }
    }

    public record MembershipSummary(
            UUID                                           id,
            @JsonProperty("susu_group_id") UUID           susuGroupId,
            @JsonProperty("user_id") UUID                 userId,
            @JsonProperty("rotation_position") Integer    rotationPosition,
            String                                         status,
            @JsonProperty("joined_at") Instant            joinedAt
    ) {
        static MembershipSummary from(SusuMembershipEntity m) {
            return new MembershipSummary(
                    m.getId(), m.getSusuGroupId(), m.getUserId(),
                    m.getRotationPosition(), m.getStatus(), m.getJoinedAt()
            );
        }
    }
}
