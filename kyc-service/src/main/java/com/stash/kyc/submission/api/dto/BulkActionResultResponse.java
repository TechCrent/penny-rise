package com.stash.kyc.submission.api.dto;

import java.util.List;

public record BulkActionResultResponse(List<BulkActionItemResult> results) {}
