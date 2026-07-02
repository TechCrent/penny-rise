package com.stash.audit.config;

import com.stash.audit.security.AdminJwtVerifier;
import com.stash.audit.security.AuditAdminJwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * audit-service's first HTTP security surface (v0.5-011).
 * @EnableWebSecurity activates Spring Security's web infrastructure;
 * SecurityAutoConfiguration is excluded in AuditServiceApplication to
 * prevent the default chain from overriding this one.
 */
@Configuration
@org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
public class AuditWebSecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, AdminJwtVerifier verifier) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/**").permitAll()
                .requestMatchers("/api/v1/admin/**").authenticated()
                .anyRequest().denyAll())
            .addFilterBefore(new AuditAdminJwtAuthenticationFilter(verifier),
                    UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
