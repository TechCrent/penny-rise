package com.stash.admin.security;

import com.stash.admin.service.AdminJwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Validates admin tokens on {@code /api/v1/admin/**} endpoints only.
 *
 * <p>Runs AFTER {@link com.stash.platform.user.security.JwtAuthenticationFilter}
 * in the filter chain. For admin endpoints, the customer filter runs first
 * and clears the context (admin tokens lack the kyc_status/subscription_tier
 * claims required by customer token verification), then this filter runs and
 * authenticates the admin token.
 *
 * <p>Rejects:
 * <ul>
 *   <li>Missing/malformed Authorization header — chain continues unauthenticated</li>
 *   <li>Invalid signature or expired — context cleared, chain continues</li>
 *   <li>Valid signature but {@code token_type != "ADMIN"} (i.e. a customer token
 *       presented on an admin endpoint) — context cleared, chain continues</li>
 * </ul>
 *
 * <p>On success, populates the Spring Security context with an authority
 * derived from {@code account_type} (e.g. {@code ROLE_SUPER}), which
 * {@code @PreAuthorize} annotations in v0.5-004 will check against.
 */
public class AdminJwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String ADMIN_PATH_PREFIX     = "/api/v1/admin/";
    // KYC admin proxy is mounted at /api/v1/kyc/admin/ on the monolith and
    // requires admin JWT auth — same filter, different path prefix.
    private static final String KYC_ADMIN_PATH_PREFIX = "/api/v1/kyc/admin/";
    private final AdminJwtService jwtService;

    public AdminJwtAuthenticationFilter(AdminJwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return !uri.startsWith(ADMIN_PATH_PREFIX) && !uri.startsWith(KYC_ADMIN_PATH_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            chain.doFilter(request, response);
            return;
        }

        String token = header.substring(7);

        try {
            var claims = jwtService.parseAndValidate(token);

            List<GrantedAuthority> authorities = List.of(
                    new SimpleGrantedAuthority("ROLE_" + claims.accountType())
            );

            var authentication = new UsernamePasswordAuthenticationToken(
                    claims.adminAccountId(), null, authorities);
            authentication.setDetails(claims);

            SecurityContextHolder.getContext().setAuthentication(authentication);

        } catch (Exception e) {
            // Invalid signature, expired, OR token_type != ADMIN (customer token
            // presented on admin endpoint). All three: do not authenticate.
            SecurityContextHolder.clearContext();
        }

        chain.doFilter(request, response);
    }
}
