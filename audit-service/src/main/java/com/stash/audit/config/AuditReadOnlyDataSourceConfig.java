package com.stash.audit.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

@Configuration
public class AuditReadOnlyDataSourceConfig {

    // Spring Boot's DataSourceAutoConfiguration backs off when it finds ANY
    // DataSource bean already defined. Since we define auditReadOnlyDataSource
    // below, we must also declare the primary read-write datasource here so
    // Flyway, JPA, and the transaction manager all get a writable connection.

    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource")
    public DataSourceProperties primaryDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource.hikari")
    public HikariDataSource dataSource(
            @Qualifier("primaryDataSourceProperties") DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
    }

    @Bean
    @Qualifier("auditReadOnlyDataSource")
    public DataSource auditReadOnlyDataSource(
            @Value("${stash.audit.readonly-datasource.jdbc-url}") String jdbcUrl,
            @Value("${stash.audit.readonly-datasource.username:audit_dashboard_ro}") String username,
            @Value("${stash.audit.readonly-datasource.password}") String password) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(username);
        config.setPassword(password);
        // Belt-and-suspenders: sets default_transaction_read_only at the JDBC
        // level, on top of (not instead of) the GRANT-level enforcement from
        // v0.5-010. Two independent layers, either one catching a write is a win.
        config.setReadOnly(true);
        config.setMaximumPoolSize(5);
        config.setPoolName("audit-readonly-pool");
        return new HikariDataSource(config);
    }

    // JdbcTemplateAutoConfiguration is suppressed by @ConditionalOnMissingBean(JdbcOperations.class)
    // when auditReadOnlyJdbcTemplate exists. Declaring the primary JdbcTemplate explicitly here
    // ensures AuditAppendOnlyGrantsCheck (and anything else that injects JdbcTemplate without a
    // qualifier) gets a connection as stash_audit, not audit_dashboard_ro. Without this, the
    // grants-check query runs as audit_dashboard_ro whose enabled_roles set doesn't include
    // stash_audit (the grantor), so information_schema.role_table_grants returns no rows for
    // grantee = 'audit_app' and the check spuriously fails.
    @Bean
    @Primary
    public JdbcTemplate jdbcTemplate(@Qualifier("dataSource") DataSource primaryDataSource) {
        return new JdbcTemplate(primaryDataSource);
    }

    @Bean
    @Qualifier("auditReadOnlyJdbcTemplate")
    public JdbcTemplate auditReadOnlyJdbcTemplate(
            @Qualifier("auditReadOnlyDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }
}
