package com.stash.audit.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

@Configuration
public class AuditReadOnlyDataSourceConfig {

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

    @Bean
    @Qualifier("auditReadOnlyJdbcTemplate")
    public JdbcTemplate auditReadOnlyJdbcTemplate(
            @Qualifier("auditReadOnlyDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }
}
