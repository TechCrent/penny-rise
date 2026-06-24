package com.stash.kyc.submission.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;
import java.util.UUID;

/**
 * Response for a successful POST /api/v1/kyc/submissions.
 *
 * <p>upload_urls maps document type to a signed upload URL the client
 * PUTs the corresponding image to. Never includes ghana_card_number —
 * the encrypted column should never be echoed back even in encrypted form.
 */
public record CreateSubmissionResponse(
        UUID id,
        String status,
        @JsonProperty("upload_urls") Map<String, String> uploadUrls
) {
    public static final String DOC_FRONT_OF_CARD = "FRONT_OF_CARD";
    public static final String DOC_BACK_OF_CARD  = "BACK_OF_CARD";
    public static final String DOC_SELFIE         = "SELFIE";
}
