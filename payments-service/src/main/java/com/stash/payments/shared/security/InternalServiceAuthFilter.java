package com.stash.payments.shared.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Guards all {@code /internal/**} routes with a shared service token.
 *
 * <p>The token is provided via the {@code X-Internal-Service-Token} header.
 * Any request to {@code /internal/**} without the correct token is rejected
 * with 401 before reaching any controller.
 *
 * <p>This filter runs at Order(5) — before the idempotency filter (Order 10)
 * so unauthenticated requests are rejected without writing idempotency rows.
 *
 * <p>In production, this path should additionally be blocked at the network
 * layer (API gateway does not route {@code /internal/**} externally). The
 * shared token is a defence-in-depth measure, not the primary access control.
 */
@Component
@Order(5)
public class InternalServiceAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(InternalServiceAuthFilter.class);
    private static final String TOKEN_HEADER = "X-Internal-Service-Token";

    private final String expectedToken;

    public InternalServiceAuthFilter(
            @Value("${stash.internal.service-token}") String expectedToken) {
        if (expectedToken == null || expectedToken.isBlank()) {
            throw new IllegalArgumentException(
                    "INTERNAL_SERVICE_TOKEN must be set. " +
                    "Generate with: openssl rand -hex 32");
        }
        this.expectedToken = expectedToken;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/internal/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String token = request.getHeader(TOKEN_HEADER);
        if (!expectedToken.equals(token)) {
            log.warn("Internal service auth failed: URI={} tokenPresent={}",
                    request.getRequestURI(), token != null);
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(
                    "{\"code\":\"INTERNAL_AUTH_FAILED\"," +
                    "\"message\":\"Missing or invalid X-Internal-Service-Token header.\"}");
            return;
        }
        chain.doFilter(request, response);
    }
}
