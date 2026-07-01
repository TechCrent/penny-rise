package com.stash.audit.startup;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Per System Design §5.4: "each service queries information_schema.role_table_grants
 * on these tables; if the unexpected permissions exist, the service refuses to start."
 *
 * Implemented as an ApplicationRunner rather than @PostConstruct so it runs
 * after Flyway migration and after the full context is up — throwing here
 * aborts Spring Boot's startup with a non-zero exit.
 */
@Component
public class AuditAppendOnlyGrantsCheck implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AuditAppendOnlyGrantsCheck.class);

    private static final String TABLE_SCHEMA = "audit";
    private static final String TABLE_NAME   = "audit_log_entries";

    private final JdbcTemplate jdbcTemplate;
    private final String appRole;
    private final String readOnlyRole;

    public AuditAppendOnlyGrantsCheck(JdbcTemplate jdbcTemplate,
                                       @Value("${stash.audit.app-role:audit_app}") String appRole,
                                       @Value("${stash.audit.readonly-role:audit_dashboard_ro}") String readOnlyRole) {
        this.jdbcTemplate = jdbcTemplate;
        this.appRole      = appRole;
        this.readOnlyRole = readOnlyRole;
    }

    @Override
    public void run(ApplicationArguments args) {
        Set<String> appGrants      = grantsFor(appRole);
        Set<String> readOnlyGrants = grantsFor(readOnlyRole);

        List<String> violations = new ArrayList<>();

        if (!appGrants.contains("INSERT")) {
            violations.add(appRole + " is missing INSERT on " + qualifiedTable());
        }
        if (!appGrants.contains("SELECT")) {
            violations.add(appRole + " is missing SELECT on " + qualifiedTable());
        }
        if (appGrants.contains("UPDATE")) {
            violations.add(appRole + " unexpectedly HAS UPDATE on " + qualifiedTable()
                    + " — append-only invariant violated");
        }
        if (appGrants.contains("DELETE")) {
            violations.add(appRole + " unexpectedly HAS DELETE on " + qualifiedTable()
                    + " — append-only invariant violated");
        }

        if (!readOnlyGrants.contains("SELECT")) {
            violations.add(readOnlyRole + " is missing SELECT on " + qualifiedTable());
        }
        Set<String> readOnlyWriteGrants = new HashSet<>(readOnlyGrants);
        readOnlyWriteGrants.retainAll(Set.of("INSERT", "UPDATE", "DELETE"));
        if (!readOnlyWriteGrants.isEmpty()) {
            violations.add(readOnlyRole + " unexpectedly HAS write privileges " + readOnlyWriteGrants
                    + " on " + qualifiedTable() + " — must be read-only");
        }

        if (!violations.isEmpty()) {
            String message = "Audit append-only GRANT invariant violated on startup:\n  - "
                    + String.join("\n  - ", violations);
            log.error("[P0_ALERT] {}", message);
            throw new IllegalStateException(message);
        }

        log.info("Audit append-only GRANT check passed: {} has INSERT+SELECT only, {} has SELECT only on {}",
                appRole, readOnlyRole, qualifiedTable());
    }

    private Set<String> grantsFor(String role) {
        List<String> privileges = jdbcTemplate.queryForList(
                """
                SELECT privilege_type FROM information_schema.role_table_grants
                WHERE table_schema = ? AND table_name = ? AND grantee = ?
                """,
                String.class, TABLE_SCHEMA, TABLE_NAME, role);
        return new HashSet<>(privileges);
    }

    private String qualifiedTable() {
        return TABLE_SCHEMA + "." + TABLE_NAME;
    }
}
