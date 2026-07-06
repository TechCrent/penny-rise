package com.stash.admin.api.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

/** Applies the same resolution to every dispute in {@code ids}. */
public record BulkResolveDisputesRequest(
        @NotEmpty List<UUID> ids,
        @NotNull JsonNode resolution
) {}
