package com.stash.kyc.document.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

public record DocumentUploadConfirmationResponse(
        UUID id,
        @JsonProperty("submission_status") String submissionStatus
) {}
