package com.stash.payments.ledger.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record StatementResponse(
        List<StatementEntryDto> entries,
        @JsonProperty("next_cursor")            String  nextCursor,
        @JsonProperty("has_more")               boolean hasMore,
        @JsonProperty("total_entries_on_page")  int     totalEntriesOnPage
) {
    public static StatementResponse of(List<StatementEntryDto> entries, String nextCursor) {
        return new StatementResponse(entries, nextCursor, nextCursor != null, entries.size());
    }
}
