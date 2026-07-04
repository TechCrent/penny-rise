package db.migration.admin;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;

/**
 * Seeds the local-dev SUPER admin account.
 *
 * <p>V20_1 reads BOOTSTRAP_ADMIN_EMAIL / BOOTSTRAP_ADMIN_PASSWORD from the OS
 * environment at migration time, but Spring's {@code config.import:.env} is not
 * visible to {@code System.getenv()} inside Flyway Java migrations (they run
 * before the Spring context exists). V20_1 therefore silently skipped creation
 * in local dev. This migration uses hardcoded local-dev defaults instead.
 *
 * <p>Idempotent: skips if any SUPER admin already exists.
 */
public class V47__EnsureLocalDevAdmin extends BaseJavaMigration {

    private static final Logger log = LoggerFactory.getLogger(V47__EnsureLocalDevAdmin.class);

    private static final String DEFAULT_EMAIL    = "admin@stash.local";
    private static final String DEFAULT_PASSWORD = "LocalDevAdmin2024!";

    @Override
    public void migrate(Context context) throws Exception {
        var connection = context.getConnection();

        try (PreparedStatement check = connection.prepareStatement(
                "SELECT COUNT(*) FROM admin.admin_accounts WHERE account_type = 'SUPER'")) {
            ResultSet rs = check.executeQuery();
            rs.next();
            if (rs.getInt(1) > 0) {
                log.info("V47: SUPER admin already exists. Nothing to do.");
                return;
            }
        }

        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);
        UUID adminId = UUID.randomUUID();

        try (PreparedStatement insert = connection.prepareStatement("""
                INSERT INTO admin.admin_accounts
                    (id, email, password_hash, full_name, role_name,
                     account_type, created_by_id, is_active, created_at)
                VALUES (?, ?, ?, ?, NULL, 'SUPER', NULL, true, now())
                """)) {
            insert.setObject(1, adminId);
            insert.setString(2, DEFAULT_EMAIL);
            insert.setString(3, encoder.encode(DEFAULT_PASSWORD));
            insert.setString(4, "Platform Administrator");
            insert.executeUpdate();
        }

        log.info("V47: Local dev SUPER admin created. email={} id={}", DEFAULT_EMAIL, adminId);
    }

    @Override
    public Integer getChecksum() {
        return null;
    }
}
