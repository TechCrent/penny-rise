package com.stash.platform.susu.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.stash.platform.susu.domain.SusuGroupEntity;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

public record SusuGroupResponse(
        UUID                                           id,
        @JsonProperty("organiser_user_id") UUID        organiserUserId,
        String                                         name,
        @JsonProperty("contribution_amount") long      contributionAmount,
        @JsonProperty("contribution_amount_cedis") String contributionAmountCedis,
        String                                         frequency,
        @JsonProperty("target_member_count") int       targetMemberCount,
        String                                         status,
        @JsonProperty("join_code") String              joinCode,
        @JsonProperty("created_at") Instant            createdAt
) {
    public static SusuGroupResponse from(SusuGroupEntity g) {
        String cedis = BigDecimal.valueOf(g.getContributionAmount())
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.FLOOR)
                .toPlainString();
        return new SusuGroupResponse(
                g.getId(), g.getOrganiserUserId(), g.getName(),
                g.getContributionAmount(), cedis,
                g.getFrequency(), g.getTargetMemberCount(),
                g.getStatus(), g.getJoinCode(),
                g.getCreatedAt()
        );
    }
}
