package com.stash.kyc.submission.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Full submission detail per Wireframes A6: submitted details, the
 * automated provider's verdict, and document view URLs for all three tabs.
 *
 * <p>ghana_card_number is intentionally included here (unlike the customer-
 * facing endpoints) — admin reviewers must see the actual number to compare
 * against the card image. This is a privileged view, gated by admin auth.
 */
public record AdminSubmissionDetailResponse(
        UUID id,
        @JsonProperty("user_id")            UUID userId,
        @JsonProperty("ghana_card_number")  String ghanaCardNumber,
        @JsonProperty("full_name_on_card")  String fullNameOnCard,
        @JsonProperty("date_of_birth")      LocalDate dateOfBirth,
        @JsonProperty("phone_number")       String phoneNumber,
        String status,
        @JsonProperty("review_path")        String reviewPath,
        @JsonProperty("submitted_at")       Instant submittedAt,
        @JsonProperty("document_view_urls") Map<String, String> documentViewUrls,
        @JsonProperty("provider_decisions") List<ProviderDecisionSummary> providerDecisions
) {
    public record ProviderDecisionSummary(
            String decision,
            @JsonProperty("confidence_score") Double confidenceScore,
            @JsonProperty("requested_at")     Instant requestedAt
    ) {}
}
