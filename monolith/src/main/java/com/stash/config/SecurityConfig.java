package com.stash.config;

import com.stash.admin.rbac.AdminAccessDeniedHandler;
import com.stash.admin.security.AdminJwtAuthenticationFilter;
import com.stash.admin.service.AdminJwtService;
import com.stash.platform.user.security.JwtAuthenticationFilter;
import com.stash.platform.user.service.JwtTokenService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Spring Security configuration for the monolith.
 *
 * <p>Stateless JWT authentication: CSRF disabled (no cookies used),
 * sessions disabled (each request is independently authenticated via
 * the Bearer token), {@link JwtAuthenticationFilter} runs before the
 * standard username/password filter.
 *
 * <p>Public endpoints (no token required): signup, login, refresh,
 * verify-email, resend-verification, password reset flow, Actuator
 * health, Swagger UI.
 *
 * <p>Everything else under /api/v1/** requires a valid JWT.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of("http://localhost:5173"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        config.setAllowedHeaders(List.of(
                HttpHeaders.AUTHORIZATION,
                HttpHeaders.CONTENT_TYPE,
                "X-Admin-Token",
                "X-Refresh-Token"
        ));
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   JwtTokenService jwtTokenService,
                                                   AdminJwtService adminJwtService,
                                                   AdminAccessDeniedHandler adminAccessDeniedHandler) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Public auth endpoints
                        .requestMatchers(
                                "/api/v1/auth/signup",
                                "/api/v1/auth/login",
                                "/api/v1/auth/refresh",
                                "/api/v1/auth/verify-email",
                                "/api/v1/auth/email-verified",
                                "/api/v1/auth/resend-verification",
                                "/api/v1/auth/forgot-password",
                                "/api/v1/auth/reset-password"
                        ).permitAll()
                        // Admin auth is public (login/refresh don't require a token)
                        .requestMatchers(
                                "/api/v1/admin/auth/login",
                                "/api/v1/admin/auth/refresh"
                        ).permitAll()
                        // Actuator and docs
                        .requestMatchers("/actuator/**").permitAll()
                        .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
                        // Internal local storage upload — unauthenticated (device-to-device in dev)
                        .requestMatchers("/internal/local-storage/**").permitAll()
                        // Everything else requires authentication
                        .anyRequest().authenticated()
                )
                // Customer filter runs first; AdminJwtAuthenticationFilter runs after,
                // limited to /api/v1/admin/** paths via its shouldNotFilter() override.
                // This ordering ensures that for admin endpoints, the customer filter
                // clears the context for admin tokens (missing kyc_status claim), and
                // the admin filter then authenticates them cleanly.
                .addFilterBefore(
                        new JwtAuthenticationFilter(jwtTokenService),
                        UsernamePasswordAuthenticationFilter.class
                )
                .addFilterAfter(
                        new AdminJwtAuthenticationFilter(adminJwtService),
                        JwtAuthenticationFilter.class
                )
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
                        .accessDeniedHandler(adminAccessDeniedHandler));

        return http.build();
    }
}
