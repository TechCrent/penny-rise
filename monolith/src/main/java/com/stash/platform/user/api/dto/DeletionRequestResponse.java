package com.stash.platform.user.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

public record DeletionRequestResponse(
        UUID id,
        String status,
        @JsonProperty("submitted_at")            Instant submittedAt,
        @JsonProperty("scheduled_completion_at") Instant scheduledCompletionAt,
        @JsonProperty("blockers_at_submission")  JsonNode blockersAtSubmission
) {}
