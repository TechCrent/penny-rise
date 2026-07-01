package com.stash.admin.api.dto;

import java.util.List;

public record AdminUserListResponse(
        List<AdminUserListItem> items,
        long totalElements,
        int  totalPages,
        int  page,
        int  size
) {}
