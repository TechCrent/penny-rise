package com.stash.kyc.submission.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

public record BulkApproveRequest(
        @NotEmpty
        @JsonProperty("submission_ids")
        List<UUID> submissionIds
) {}
