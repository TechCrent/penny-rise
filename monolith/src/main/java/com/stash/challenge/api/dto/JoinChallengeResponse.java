package com.stash.challenge.api.dto;

import java.time.Instant;
import java.util.UUID;

public record JoinChallengeResponse(
        UUID id,
        UUID challengeId,
        String status,
        Long targetAmount,
        long progressAmount,
        Instant enrolledAt) {}
