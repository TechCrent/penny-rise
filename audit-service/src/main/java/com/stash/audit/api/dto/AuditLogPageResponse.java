package com.stash.audit.api.dto;

import java.util.List;

public record AuditLogPageResponse(List<AuditLogEntryResponse> entries, String nextCursor, boolean hasMore) {}
