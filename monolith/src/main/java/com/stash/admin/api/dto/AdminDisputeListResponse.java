package com.stash.admin.api.dto;

import java.util.List;

public record AdminDisputeListResponse(
        List<AdminDisputeListItem> disputes, int page, int size, long totalElements, int totalPages
) {}
