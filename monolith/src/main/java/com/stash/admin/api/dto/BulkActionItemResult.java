package com.stash.admin.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

public record BulkActionItemResult(
        UUID id,
        boolean success,
        @JsonProperty("error_message") String errorMessage
) {}
