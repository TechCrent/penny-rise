package com.stash.payments.config;

import org.flywaydb.core.Flyway;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;

import javax.sql.DataSource;

/**
 * Each migration folder below independently starts at V1 (see
 * {@code db/migration/MIGRATIONS.md}-equivalent convention per folder),
 * but Spring Boot's single auto-configured Flyway bean treats every
 * {@code locations} entry as one shared, flat version history — it
 * rejects the resulting duplicate "V1" across folders. Each folder
 * therefore gets its own {@link Flyway} instance and its own schema
 * history table (spring.flyway.enabled=false in application.yml
 * disables the combined bean this replaces).
 *
 * <p>{@code transaction} and {@code paystack} have foreign keys into
 * {@code ledger.*}, so {@code ledger} must migrate first — enforced via
 * {@code @DependsOn} rather than relying on bean declaration order.
 *
 * <p>Beans of type {@link FlywayMigrationInitializer} are auto-detected
 * by Spring Boot's {@code FlywayMigrationInitializerDatabaseInitializerDetector},
 * which makes the JPA {@code entityManagerFactory} depend on all of them —
 * the same mechanism the single auto-configured bean relied on.
 */
@Configuration
public class FlywayMultiSchemaConfig {

    @Bean
    public FlywayMigrationInitializer ledgerFlywayInitializer(DataSource dataSource) {
        return migrationInitializer(dataSource, "ledger");
    }

    @Bean
    @DependsOn("ledgerFlywayInitializer")
    public FlywayMigrationInitializer transactionFlywayInitializer(DataSource dataSource) {
        return migrationInitializer(dataSource, "transaction");
    }

    @Bean
    @DependsOn("ledgerFlywayInitializer")
    public FlywayMigrationInitializer paystackFlywayInitializer(DataSource dataSource) {
        return migrationInitializer(dataSource, "paystack");
    }

    @Bean
    public FlywayMigrationInitializer outboxFlywayInitializer(DataSource dataSource) {
        return migrationInitializer(dataSource, "outbox");
    }

    @Bean
    public FlywayMigrationInitializer webhookFlywayInitializer(DataSource dataSource) {
        return migrationInitializer(dataSource, "webhook");
    }

    @Bean
    public FlywayMigrationInitializer idempotencyFlywayInitializer(DataSource dataSource) {
        return migrationInitializer(dataSource, "idempotency");
    }

    private FlywayMigrationInitializer migrationInitializer(DataSource dataSource, String folder) {
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration/" + folder)
                .table("flyway_schema_history_" + folder)
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .outOfOrder(false)
                .load();
        return new FlywayMigrationInitializer(flyway);
    }
}
