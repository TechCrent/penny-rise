package com.stash.admin.api.dto;

import jakarta.validation.constraints.NotBlank;

public record SuspendUserRequest(@NotBlank String reason) {}
