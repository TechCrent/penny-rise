package com.stash.platform.transfer.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record TransferListResponse(
        List<TransferListItemResponse>          transfers,
        @JsonProperty("next_cursor") String     nextCursor,
        @JsonProperty("has_more")    boolean    hasMore,
        int                                      count
) {
    public static TransferListResponse of(List<TransferListItemResponse> items,
                                           String nextCursor) {
        return new TransferListResponse(items, nextCursor, nextCursor != null, items.size());
    }
}
