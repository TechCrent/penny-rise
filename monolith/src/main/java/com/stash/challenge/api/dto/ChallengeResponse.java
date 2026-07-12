package com.stash.challenge.api.dto;

import java.util.UUID;

/**
 * GET /api/v1/challenges and GET /api/v1/challenges/{id} response shape —
 * matches the mobile app's proposed contract in
 * mobile/src/screens/Challenges/types.ts verbatim (that file documents this
 * was the only side of the contract that existed until now).
 */
public record ChallengeResponse(
        UUID id,
        String name,
        String description,
        Long targetAmount,
        Integer targetDurationDays,
        String badgeCode,
        String badgeName,
        String badgeAssetName,
        Enrollment enrollment) {

    public record Enrollment(
            String status,
            long progressAmount,
            java.time.Instant enrolledAt,
            java.time.Instant completedAt) {}
}
