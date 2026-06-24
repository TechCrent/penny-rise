package com.stash.platform.user.security;

import com.stash.platform.user.service.AccessTokenClaims;
import com.stash.platform.user.service.JwtTokenService;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Validates the Bearer JWT on every request and populates
 * {@link SecurityContextHolder} with an {@link AuthenticatedUser}.
 *
 * <p>Requests without a token, or with an invalid token, proceed through
 * the filter chain unauthenticated — Spring Security's
 * {@code authorizeHttpRequests} rules decide whether the endpoint requires
 * authentication. This filter only ever ADDS authentication; it never
 * rejects a request directly (that's the job of the security config).
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenService jwtTokenService;

    public JwtAuthenticationFilter(JwtTokenService jwtTokenService) {
        this.jwtTokenService = jwtTokenService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String header = request.getHeader("Authorization");

        if (header != null && header.startsWith(BEARER_PREFIX)) {
            String token = header.substring(BEARER_PREFIX.length());
            // DO NOT log the token value

            try {
                AccessTokenClaims claims = jwtTokenService.verify(token);
                AuthenticatedUser authenticatedUser = new AuthenticatedUser(claims);
                SecurityContextHolder.getContext().setAuthentication(authenticatedUser);
            } catch (JwtException e) {
                // Invalid token — leave SecurityContext empty.
                // The security config will reject the request if the endpoint requires auth.
                log.debug("JWT validation failed: {}", e.getClass().getSimpleName());
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }
}