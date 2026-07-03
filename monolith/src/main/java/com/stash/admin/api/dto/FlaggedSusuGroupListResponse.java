package com.stash.admin.api.dto;

import java.util.List;

public record FlaggedSusuGroupListResponse(
        List<FlaggedSusuGroupListItem> items, int page, int size, long totalElements, int totalPages) {}
