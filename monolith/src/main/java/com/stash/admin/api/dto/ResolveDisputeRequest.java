package com.stash.admin.api.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;

public record ResolveDisputeRequest(@NotNull JsonNode resolution) {}
