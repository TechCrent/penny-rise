package com.stash.payments.shared.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Populates {@link CallerContext} as a request attribute for use by
 * controllers and services.
 *
 * <p>Runs at Order(15) — after the idempotency filter (Order 10) and
 * after Spring Security has processed the JWT. Reads either:
 * <ul>
 *   <li>The {@code X-Internal-Service-Token} header → {@link CallerContext#internal()}</li>
 *   <li>The authenticated user principal from the JWT → {@link CallerContext#user(UUID)}</li>
 * </ul>
 */
@Component
@Order(15)
public class CallerContextFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(CallerContextFilter.class);
    public static final  String ATTR = "callerContext";

    private final String internalToken;

    public CallerContextFilter(
            @Value("${stash.internal.service-token}") String internalToken) {
        this.internalToken = internalToken;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String token = request.getHeader("X-Internal-Service-Token");
        if (internalToken.equals(token)) {
            request.setAttribute(ATTR, CallerContext.internal());
        } else {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof String userId) {
                try {
                    request.setAttribute(ATTR, CallerContext.user(UUID.fromString(userId)));
                } catch (IllegalArgumentException e) {
                    log.warn("JWT principal is not a valid UUID: {}", userId);
                }
            }
        }
        chain.doFilter(request, response);
    }
}
