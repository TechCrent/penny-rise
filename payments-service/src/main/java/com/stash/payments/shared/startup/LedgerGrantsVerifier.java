package com.stash.payments.shared.startup;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Startup guard that verifies Postgres-level append-only grants on the
 * tables that require them. Fails fast on boot if UPDATE or DELETE are
 * discoverable by the application role — a condition that indicates the
 * migration was never applied or was manually rolled back.
 *
 * <p>Runs after the application context is fully loaded (ApplicationReadyEvent)
 * so the datasource and connection pool are ready.
 *
 * <p>Tables checked:
 * <ul>
 *   <li>{@code ledger.ledger_entries} — INSERT/SELECT only (v0.3-003)</li>
 *   <li>{@code outbox.outbox_events} — INSERT/SELECT + restricted UPDATE
 *       (v0.3-006 extends this class)</li>
 * </ul>
 *
 * <p>How the check works: the application role connects and attempts a
 * targeted privilege query against {@code information_schema.role_table_grants}.
 * If UPDATE or DELETE appear for a table that must be append-only, the
 * service throws {@link IllegalStateException} and refuses to finish starting.
 *
 * @see <a href="https://www.postgresql.org/docs/current/infoschema-role-table-grants.html">
 *     PostgreSQL role_table_grants</a>
 */
@Component
public class LedgerGrantsVerifier {

    private static final Logger log = LoggerFactory.getLogger(LedgerGrantsVerifier.class);

    /**
     * The Postgres application role name, matching the role created in v0.1-006.
     * If your environment uses a different role, update this constant and the
     * migration REVOKE statement together.
     */
    static final String APP_ROLE = "stash_payments";

    /**
     * Tables that must never allow UPDATE or DELETE from the application role.
     * Add entries here when v0.3-006 (outbox) enforces the same.
     */
    private static final List<TableSpec> APPEND_ONLY_TABLES = List.of(
            new TableSpec("ledger", "ledger_entries")
            // v0.3-006 will add: new TableSpec("outbox", "outbox_events")
    );

    private final JdbcTemplate jdbc;

    public LedgerGrantsVerifier(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void verifyOnStartup() {
        log.info("LedgerGrantsVerifier: checking append-only grants for role '{}'", APP_ROLE);

        for (TableSpec table : APPEND_ONLY_TABLES) {
            checkNoMutatingGrants(table);
        }

        log.info("LedgerGrantsVerifier: all append-only grant checks passed.");
    }

    private void checkNoMutatingGrants(TableSpec table) {
        String sql = """
                SELECT privilege_type
                FROM information_schema.role_table_grants
                WHERE grantee        = ?
                  AND table_schema   = ?
                  AND table_name     = ?
                  AND privilege_type IN ('UPDATE', 'DELETE')
                """;

        List<String> forbidden = jdbc.queryForList(
                sql,
                String.class,
                APP_ROLE, table.schema(), table.name()
        );

        if (!forbidden.isEmpty()) {
            String msg = String.format(
                    "FATAL: Append-only violation on %s.%s — role '%s' has %s privileges. " +
                    "The migration REVOKE was not applied or was manually undone. " +
                    "Refusing to start to protect ledger integrity.",
                    table.schema(), table.name(), APP_ROLE, forbidden
            );
            log.error(msg);
            throw new IllegalStateException(msg);
        }

        log.info("LedgerGrantsVerifier: {}.{} — OK (no UPDATE/DELETE for '{}')",
                table.schema(), table.name(), APP_ROLE);
    }

    record TableSpec(String schema, String name) {}
}
