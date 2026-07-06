package com.stash.admin.api.dto;

import java.util.List;

public record BulkActionResultResponse(List<BulkActionItemResult> results) {}
