package com.stash.kyc.submission.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Per the Wireframes doc A5: queue card shows applicant info, submission
 * time, status pill, optional flag reason. document_view_urls lets the
 * admin console render thumbnails directly from the queue list without
 * a second round-trip per card.
 */
public record AdminQueueItemResponse(
        @JsonProperty("submission_id")    UUID submissionId,
        @JsonProperty("user_id")          UUID userId,
        @JsonProperty("full_name_on_card") String fullNameOnCard,
        @JsonProperty("submitted_at")     Instant submittedAt,
        @JsonProperty("flag_reason")      String flagReason,
        @JsonProperty("document_view_urls") Map<String, String> documentViewUrls
) {}
