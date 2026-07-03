package com.stash.audit.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stash.audit.api.dto.AuditLogEntryResponse;
import com.stash.audit.api.dto.AuditLogFacetsResponse;
import com.stash.audit.api.dto.AuditLogPageResponse;
import com.stash.audit.api.dto.AuditLogQueryFilter;
import com.stash.audit.repository.AuditLogEntryRow;
import com.stash.audit.repository.AuditLogQueryRepository;
import com.stash.audit.repository.CursorPosition;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class AuditLogQueryService {

    static final int DEFAULT_LIMIT = 25;
    static final int MAX_LIMIT     = 100;

    private final AuditLogQueryRepository repository;
    private final ObjectMapper objectMapper;

    public AuditLogQueryService(AuditLogQueryRepository repository, ObjectMapper objectMapper) {
        this.repository   = repository;
        this.objectMapper = objectMapper;
    }

    public AuditLogPageResponse list(AuditLogQueryFilter filter, String cursorEventId, Integer requestedLimit) {
        int limit  = clampLimit(requestedLimit);

        CursorPosition cursor = null;
        if (cursorEventId != null) {
            cursor = repository.resolveCursor(cursorEventId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "INVALID_CURSOR: The provided cursor event_id does not exist."));
        }

        List<AuditLogEntryRow> rows    = repository.query(filter, cursor, limit + 1);
        boolean                hasMore = rows.size() > limit;
        List<AuditLogEntryRow> page    = hasMore ? rows.subList(0, limit) : rows;
        String nextCursor = hasMore ? page.get(page.size() - 1).eventId() : null;

        return new AuditLogPageResponse(page.stream().map(this::toResponse).toList(), nextCursor, hasMore);
    }

    public AuditLogFacetsResponse getFacets() {
        return new AuditLogFacetsResponse(
                repository.findDistinctEventTypes(), repository.findDistinctTargetTypes());
    }

    private int clampLimit(Integer requested) {
        if (requested == null) return DEFAULT_LIMIT;
        return Math.min(Math.max(requested, 1), MAX_LIMIT);
    }

    private AuditLogEntryResponse toResponse(AuditLogEntryRow row) {
        JsonNode payloadNode;
        try {
            payloadNode = objectMapper.readTree(row.payload());
        } catch (Exception e) {
            payloadNode = objectMapper.getNodeFactory().nullNode();
        }
        return new AuditLogEntryResponse(
                row.eventId(), row.eventType(), row.actorType(), row.actorId(),
                row.targetType(), row.targetId(),
                payloadNode, row.occurredAt());
    }
}
