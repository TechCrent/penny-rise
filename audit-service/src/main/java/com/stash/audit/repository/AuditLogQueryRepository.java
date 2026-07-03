package com.stash.audit.repository;

import com.stash.audit.api.dto.AuditLogQueryFilter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class AuditLogQueryRepository {

    private final JdbcTemplate readOnlyJdbcTemplate;

    public AuditLogQueryRepository(
            @Qualifier("auditReadOnlyJdbcTemplate") JdbcTemplate readOnlyJdbcTemplate) {
        this.readOnlyJdbcTemplate = readOnlyJdbcTemplate;
    }

    public Optional<CursorPosition> resolveCursor(String eventId) {
        try {
            var row = readOnlyJdbcTemplate.queryForMap(
                    "SELECT id, occurred_at FROM audit.audit_log_entries WHERE event_id = ?", eventId);
            return Optional.of(new CursorPosition(
                    (UUID) row.get("id"),
                    ((Timestamp) row.get("occurred_at")).toInstant()));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    /**
     * limit is the caller's requested page size + 1 — used to detect hasMore
     * without a separate COUNT query.
     */
    public List<AuditLogEntryRow> query(AuditLogQueryFilter filter, CursorPosition cursor, int limit) {
        StringBuilder sql = new StringBuilder("""
                SELECT event_id, event_type, actor_type, actor_id, target_type, target_id,
                       payload, occurred_at, id
                FROM audit.audit_log_entries
                WHERE 1=1
                """);
        List<Object> params = new ArrayList<>();

        if (filter.actorId()          != null) { sql.append(" AND actor_id = ?");     params.add(filter.actorId()); }
        if (filter.actorType()        != null) { sql.append(" AND actor_type = ?");   params.add(filter.actorType()); }
        if (filter.targetEntityId()   != null) { sql.append(" AND target_id = ?");    params.add(filter.targetEntityId()); }
        if (filter.targetEntityType() != null) { sql.append(" AND target_type = ?");  params.add(filter.targetEntityType()); }
        if (filter.eventType()        != null) { sql.append(" AND event_type = ?");   params.add(filter.eventType()); }
        if (filter.fromDate()         != null) { sql.append(" AND occurred_at >= ?"); params.add(Timestamp.from(filter.fromDate())); }
        if (filter.toDate()           != null) { sql.append(" AND occurred_at <= ?"); params.add(Timestamp.from(filter.toDate())); }

        if (cursor != null) {
            sql.append(" AND (occurred_at, id) < (?, ?)");
            params.add(Timestamp.from(cursor.occurredAt()));
            params.add(cursor.id());
        }

        sql.append(" ORDER BY occurred_at DESC, id DESC LIMIT ?");
        params.add(limit);

        return readOnlyJdbcTemplate.query(sql.toString(),
                (rs, rowNum) -> new AuditLogEntryRow(
                        rs.getString("event_id"), rs.getString("event_type"),
                        rs.getString("actor_type"), (UUID) rs.getObject("actor_id"),
                        rs.getString("target_type"), (UUID) rs.getObject("target_id"),
                        rs.getString("payload"), rs.getTimestamp("occurred_at").toInstant(),
                        (UUID) rs.getObject("id")),
                params.toArray());
    }

    /**
     * v0.5-033: distinct values actually present in the data, not a
     * maintained hardcoded list — self-updates as new event types get
     * wired into the mapper registry elsewhere, rather than drifting out
     * of sync with one.
     */
    public List<String> findDistinctEventTypes() {
        return readOnlyJdbcTemplate.queryForList(
                "SELECT DISTINCT event_type FROM audit.audit_log_entries ORDER BY event_type", String.class);
    }

    public List<String> findDistinctTargetTypes() {
        return readOnlyJdbcTemplate.queryForList(
                "SELECT DISTINCT target_type FROM audit.audit_log_entries ORDER BY target_type", String.class);
    }
}
