package com.stash.payments.shared.startup;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * Startup guard that verifies Postgres-level append-only grants.
 * Fails fast on boot if the expected grant configuration is missing or
 * has been manually relaxed.
 *
 * <p>Two check types:
 * <ul>
 *   <li>{@code FULL_REVOKE} — table must have no UPDATE or DELETE grants
 *       at either the table or column level. Used for {@code ledger.ledger_entries}.</li>
 *   <li>{@code COLUMN_RESTRICTED_UPDATE} — table must have no table-level UPDATE,
 *       but must have column-level UPDATE on exactly the specified columns.
 *       Used for {@code outbox.outbox_events}.</li>
 * </ul>
 *
 * @see <a href="https://www.postgresql.org/docs/current/infoschema-role-table-grants.html">role_table_grants</a>
 * @see <a href="https://www.postgresql.org/docs/current/infoschema-role-column-grants.html">role_column_grants</a>
 */
@Component
public class LedgerGrantsVerifier {

    private static final Logger log = LoggerFactory.getLogger(LedgerGrantsVerifier.class);

    static final String APP_ROLE = "stash_payments";

    /**
     * Tables whose UPDATE and DELETE are fully revoked at the table level.
     * No column-level UPDATE either.
     */
    private static final List<TableSpec> FULL_REVOKE_TABLES = List.of(
            new TableSpec("ledger", "ledger_entries")
    );

    /**
     * Tables where table-level UPDATE is revoked, but column-level UPDATE
     * is granted on a specific restricted set of columns.
     */
    private static final List<ColumnRestrictedSpec> COLUMN_RESTRICTED_TABLES = List.of(
            new ColumnRestrictedSpec(
                    "outbox", "outbox_events",
                    Set.of("status", "attempts", "sent_at", "last_attempted_at")
            )
    );

    private final JdbcTemplate jdbc;

    public LedgerGrantsVerifier(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void verifyOnStartup() {
        log.info("LedgerGrantsVerifier: verifying append-only grants for role '{}'", APP_ROLE);

        for (TableSpec table : FULL_REVOKE_TABLES) {
            checkNoTableLevelMutatingGrants(table.schema(), table.name());
        }

        for (ColumnRestrictedSpec spec : COLUMN_RESTRICTED_TABLES) {
            checkNoTableLevelMutatingGrants(spec.schema(), spec.name());
            checkColumnLevelUpdateGrants(spec);
        }

        log.info("LedgerGrantsVerifier: all append-only grant checks passed.");
    }

    // ── Check 1: no table-level UPDATE or DELETE ──────────────────────────

    private void checkNoTableLevelMutatingGrants(String schema, String table) {
        String sql = """
                SELECT privilege_type
                FROM information_schema.role_table_grants
                WHERE grantee        = ?
                  AND table_schema   = ?
                  AND table_name     = ?
                  AND privilege_type IN ('UPDATE', 'DELETE')
                """;

        List<String> forbidden = jdbc.queryForList(sql, String.class,
                APP_ROLE, schema, table);

        if (!forbidden.isEmpty()) {
            fail(String.format(
                    "Table-level append-only violation on %s.%s — role '%s' has %s. " +
                    "The migration REVOKE was not applied or was manually undone.",
                    schema, table, APP_ROLE, forbidden));
        }

        log.info("LedgerGrantsVerifier: {}.{} table-level — OK", schema, table);
    }

    // ── Check 2: column-level UPDATE on exactly the expected set ─────────

    private void checkColumnLevelUpdateGrants(ColumnRestrictedSpec spec) {
        String sql = """
                SELECT column_name
                FROM information_schema.role_column_grants
                WHERE grantee        = ?
                  AND table_schema   = ?
                  AND table_name     = ?
                  AND privilege_type = 'UPDATE'
                """;

        List<String> grantedColumns = jdbc.queryForList(sql, String.class,
                APP_ROLE, spec.schema(), spec.name());

        Set<String> granted = Set.copyOf(grantedColumns);
        Set<String> expected = spec.allowedUpdateColumns();

        // Columns that were granted but should not have been
        Set<String> unexpected = new java.util.HashSet<>(granted);
        unexpected.removeAll(expected);

        // Columns that should be granted but weren't
        Set<String> missing = new java.util.HashSet<>(expected);
        missing.removeAll(granted);

        if (!unexpected.isEmpty()) {
            fail(String.format(
                    "Column-level UPDATE granted on unexpected columns of %s.%s: %s. " +
                    "These columns should be append-only.",
                    spec.schema(), spec.name(), unexpected));
        }

        if (!missing.isEmpty()) {
            fail(String.format(
                    "Column-level UPDATE missing on expected columns of %s.%s: %s. " +
                    "The relay worker will not be able to update these columns.",
                    spec.schema(), spec.name(), missing));
        }

        log.info("LedgerGrantsVerifier: {}.{} column-level UPDATE — OK ({})",
                spec.schema(), spec.name(), granted);
    }

    private void fail(String message) {
        String full = "FATAL: " + message + " Refusing to start.";
        log.error(full);
        throw new IllegalStateException(full);
    }

    // ── Specs ─────────────────────────────────────────────────────────────

    record TableSpec(String schema, String name) {}

    record ColumnRestrictedSpec(
            String schema,
            String name,
            Set<String> allowedUpdateColumns
    ) {}
}
