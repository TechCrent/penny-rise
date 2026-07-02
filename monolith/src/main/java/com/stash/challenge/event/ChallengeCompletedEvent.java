package com.stash.challenge.event;

import java.time.Instant;
import java.util.UUID;

public record ChallengeCompletedEvent(
        UUID userChallengeId, UUID userId, UUID challengeId, Instant occurredAt) {}
