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
 * Bootstraps the platform's initial SUPER admin account from environment
 * variables, so the system is operable immediately after first deploy
 * without a manual SQL step or a chicken-and-egg "who creates the first
 * admin" problem.
 *
 * <p><strong>Why a Java migration, not SQL:</strong> Flyway SQL migrations
 * are static files with no access to the runtime environment. They cannot
 * read {@code System.getenv()}, and they cannot call a password hashing
 * library. A Java-based migration runs in the same JVM as the application,
 * in the same Flyway version chain as the SQL migrations, and can do both.
 *
 * <p><strong>Required environment variables:</strong>
 * <ul>
 *   <li>{@code BOOTSTRAP_ADMIN_EMAIL} — the initial SUPER admin's email</li>
 *   <li>{@code BOOTSTRAP_ADMIN_PASSWORD} — plaintext password, hashed here
 *       with BCrypt cost 12 before storage; never logged, never stored
 *       plaintext anywhere</li>
 * </ul>
 *
 * <p><strong>Idempotency:</strong> if a SUPER admin row already exists
 * (e.g. this migration re-runs in a CI pipeline that resets and re-applies
 * migrations against a database that was only partially torn down), this
 * migration is a no-op. Flyway migrations only run once per database under
 * normal operation (tracked in {@code flyway_schema_history}), so this
 * check is a defensive belt-and-suspenders, not the primary mechanism.
 *
 * <p><strong>Local/dev fallback:</strong> if the environment variables are
 * absent (e.g. a developer running the app locally without secrets
 * configured), this migration logs a WARN and skips bootstrap entirely
 * rather than failing the migration chain. The developer must set the
 * variables or insert a SUPER admin manually for local admin console
 * testing. This avoids blocking all other migrations (including the
 * non-admin ones that come after V20) just because admin bootstrap
 * secrets aren't configured in dev.
 *
 * <p><strong>Production safety:</strong> in any environment where
 * {@code SPRING_PROFILES_ACTIVE} includes "production" or "staging", the
 * absence of these env vars throws and fails the migration — silently
 * skipping bootstrap in a real environment would leave the platform with
 * no way to create any admin account at all.
 */
public class V20_1__BootstrapSuperAdmin extends BaseJavaMigration {

    private static final Logger log =
            LoggerFactory.getLogger(V20_1__BootstrapSuperAdmin.class);

    private static final String ENV_EMAIL    = "BOOTSTRAP_ADMIN_EMAIL";
    private static final String ENV_PASSWORD = "BOOTSTRAP_ADMIN_PASSWORD";

    @Override
    public void migrate(Context context) throws Exception {
        var connection = context.getConnection();

        // ── Idempotency guard ──────────────────────────────────────────
        try (PreparedStatement check = connection.prepareStatement(
                "SELECT COUNT(*) FROM admin.admin_accounts WHERE account_type = 'SUPER'")) {
            ResultSet rs = check.executeQuery();
            rs.next();
            if (rs.getInt(1) > 0) {
                log.info("V20_1: SUPER admin already exists. Skipping bootstrap.");
                return;
            }
        }

        // ── Read environment variables ────────────────────────────────
        String email    = System.getenv(ENV_EMAIL);
        String password  = System.getenv(ENV_PASSWORD);
        String activeProfiles = System.getenv("SPRING_PROFILES_ACTIVE");
        boolean isProductionLike = activeProfiles != null &&
                (activeProfiles.contains("production") || activeProfiles.contains("staging"));

        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            if (isProductionLike) {
                throw new IllegalStateException(
                        "V20_1: " + ENV_EMAIL + " and " + ENV_PASSWORD +
                        " must both be set in production/staging environments. " +
                        "Without them, no admin account can ever be created — " +
                        "the platform would be permanently inadmissible to its own staff.");
            }
            log.warn("V20_1: {} and/or {} not set. Skipping SUPER admin bootstrap " +
                     "(this is OK for local dev — insert a SUPER admin manually if you " +
                     "need to test the admin console). NOT skipped in staging/production.",
                    ENV_EMAIL, ENV_PASSWORD);
            return;
        }

        String normalisedEmail = email.toLowerCase().trim();

        if (!normalisedEmail.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new IllegalStateException(
                    "V20_1: " + ENV_EMAIL + " is not a valid email address: " + normalisedEmail);
        }

        if (password.length() < 12) {
            throw new IllegalStateException(
                    "V20_1: " + ENV_PASSWORD + " must be at least 12 characters. " +
                    "This is the platform's single point of administrative access — " +
                    "a weak bootstrap password is a critical security risk.");
        }

        // ── Hash the password ────────────────────────────────────────────
        // BCrypt cost 12 per Schema doc §2.1. The encoder is instantiated
        // directly here rather than injected — Flyway Java migrations run
        // outside the Spring context, before the application is fully
        // wired up, so dependency injection is not available.
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);
        String passwordHash = encoder.encode(password);

        // ── Insert the bootstrap SUPER admin ──────────────────────────────
        UUID adminId = UUID.randomUUID();

        try (PreparedStatement insert = connection.prepareStatement("""
                INSERT INTO admin.admin_accounts
                    (id, email, password_hash, full_name, role_name,
                     account_type, created_by_id, is_active, created_at)
                VALUES (?, ?, ?, ?, NULL, 'SUPER', NULL, true, now())
                """)) {
            insert.setObject(1, adminId);
            insert.setString(2, normalisedEmail);
            insert.setString(3, passwordHash);
            insert.setString(4, "Platform Administrator");
            insert.executeUpdate();
        }

        // Deliberately do NOT log the email or password — even the hashed
        // password should never appear in build/migration logs that might
        // be retained or shipped to a log aggregator.
        log.info("V20_1: Bootstrap SUPER admin created successfully. id={}", adminId);
    }

    @Override
    public Integer getChecksum() {
        return null;
    }
}
