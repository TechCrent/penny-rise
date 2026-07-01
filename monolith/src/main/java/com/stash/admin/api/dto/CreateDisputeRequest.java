package com.stash.admin.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateDisputeRequest(
        @NotBlank String disputeType,
        @NotBlank String relatedEntityType,
        @NotNull UUID relatedEntityId,
        @NotBlank @Size(max = 255) String subject,
        @NotBlank String description
) {}
