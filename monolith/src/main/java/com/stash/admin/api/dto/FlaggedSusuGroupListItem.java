package com.stash.admin.api.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * "Last shortfall" reflects the most recent waived-penalty contribution for
 * the group (SusuLatePenaltyProcessor's real waive paths — see
 * SusuGroupFlaggingListener), not a round-level expected/actual pot amount
 * comparison. lastShortfallRoundNumber/MemberUserId/At are null if no
 * waived-penalty contribution can be found (e.g. the flag was cleared and
 * re-set by a later event whose contribution row changed since).
 */
public record FlaggedSusuGroupListItem(
        UUID id, String name, UUID organiserUserId, Instant flaggedAt,
        Integer lastShortfallRoundNumber, UUID lastShortfallMemberUserId, Instant lastShortfallAt,
        long potBalancePesewas) {}
