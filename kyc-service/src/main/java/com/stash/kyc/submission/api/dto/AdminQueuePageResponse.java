package com.stash.kyc.submission.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** Cursor-based pagination per System Design §14.4. */
public record AdminQueuePageResponse(
        List<AdminQueueItemResponse> items,
        @JsonProperty("next_cursor") String nextCursor,
        @JsonProperty("has_more")    boolean hasMore
) {}
