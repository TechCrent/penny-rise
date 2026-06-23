package com.stash.kyc.submission.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

/**
 * Customer-facing submission status response.
 *
 * <p><strong>Field exposure contract:</strong> this record structurally
 * cannot carry ghana_card_number, storage paths, or document view URLs —
 * there is no field for any of them. The admin-facing
 * {@link AdminSubmissionDetailResponse} is a separate, privileged DTO
 * used only by admin endpoints; this one is what the mobile app sees.
 *
 * <p>updated_at is derived: it's decided_at if the submission has reached
 * a terminal state, otherwise submitted_at (there is no separate
 * "last modified" column on kyc.submissions — the service computes this
 * rather than the schema tracking it explicitly).
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record SubmissionStatusResponse(
        UUID id,
        String status,
        @JsonProperty("submitted_at")     Instant submittedAt,
        @JsonProperty("updated_at")       Instant updatedAt,
        @JsonProperty("rejection_reason") String rejectionReason
) {}
