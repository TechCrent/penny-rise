package com.stash.platform.susu.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;

public record CreateSusuGroupRequest(

        @NotBlank(message = "name is required")
        @Size(max = 100, message = "name must be 100 characters or fewer")
        String name,

        @Min(value = 1, message = "contribution_amount must be at least 1 pesewa")
        @JsonProperty("contribution_amount")
        long contributionAmount,

        @NotBlank(message = "frequency is required")
        String frequency,

        @Min(value = 4, message = "target_member_count must be at least 4")
        @Max(value = 20, message = "target_member_count must be at most 20")
        @JsonProperty("target_member_count")
        int targetMemberCount
) {}
