package com.stash.payments.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Minimal security configuration for the Payments Service.
 *
 * <p>The service is internal (called by the monolith, not by browsers).
 * CSRF is disabled — all callers are server-to-server. The webhook
 * endpoint is explicitly permitted without credentials; HMAC-SHA512
 * verification inside PaystackWebhookService is the sole auth mechanism
 * for that path.
 */
@Configuration
@EnableWebSecurity
public class PaymentsSecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .httpBasic(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/webhooks/paystack").permitAll()
                .requestMatchers("/internal/**").permitAll()
                .anyRequest().permitAll()
            );
        return http.build();
    }
}
