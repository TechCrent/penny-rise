package com.stash.platform.susu.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record JoinSusuGroupRequest(
        @NotBlank(message = "join_code is required")
        @Size(min = 8, max = 8, message = "join_code must be exactly 8 characters")
        @JsonProperty("join_code")
        String joinCode
) {}
