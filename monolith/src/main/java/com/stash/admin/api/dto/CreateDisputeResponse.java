package com.stash.admin.api.dto;

import java.util.UUID;

public record CreateDisputeResponse(UUID id, String status, String priority) {}
