package com.stash.admin.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record AdminStaffListResponse(
        List<AdminStaffSummaryResponse> items,
        int page,
        int size,
        @JsonProperty("total_elements") long totalElements,
        @JsonProperty("total_pages")    int totalPages
) {}
