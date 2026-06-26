package com.stash.platform.vault.api.dto;

import jakarta.validation.constraints.NotBlank;

public record EarlyExitRequest(
        @NotBlank(message = "reason is required")
        String reason       // SCHOOL_FEES_EMERGENCY, MEDICAL, FAMILY, OTHER
) {}
