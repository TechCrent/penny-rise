package com.stash.kyc.config;

import com.stash.kyc.shared.security.AdminJwtAuthenticationFilter;
import com.stash.kyc.shared.security.AdminJwtVerifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * kyc-service's HTTP security surface. Gates {@code /api/v1/kyc/admin/**}
 * behind a real per-admin JWT ({@link AdminJwtAuthenticationFilter}),
 * replacing the {@code PlaceholderAdminAuthFilter} shared-secret check.
 * Every other route (customer-facing submission/document endpoints,
 * internal local-storage view, actuator, swagger) keeps its existing
 * (unchanged) access — only the admin surface's auth model changes here.
 *
 * <p>{@code SecurityAutoConfiguration} is excluded in
 * {@code KycServiceApplication}, so this is the only filter chain in effect.
 */
@Configuration
@EnableWebSecurity
public class KycWebSecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, AdminJwtVerifier verifier) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v1/kyc/admin/**").authenticated()
                .anyRequest().permitAll())
            .addFilterBefore(new AdminJwtAuthenticationFilter(verifier),
                    UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
