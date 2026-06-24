package com.stash.kyc.shared.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * ⚠️ PLACEHOLDER — NOT REAL ADMIN AUTHENTICATION ⚠️
 *
 * <p>Per this issue's explicit scope: "the admin auth system ships fully
 * in v0.5, so a placeholder admin token check is acceptable here."
 *
 * <p>This checks a single shared-secret header against a configured value.
 * There is no per-admin identity, no RBAC, no session — it is a binary
 * "is this request from someone who knows the shared secret" gate, useful
 * only for keeping these endpoints out of reach of the public internet
 * during local development.
 *
 * <p><strong>This filter MUST be deleted, not extended, when v0.5's real
 * admin auth system ships.</strong> Do not attempt to evolve this into
 * the real system — replace it wholesale with proper admin JWT validation
 * against admin.admin_accounts (System Design §7.2).
 *
 * <p>Since there is no real admin identity yet, {@link #PLACEHOLDER_ADMIN_ID}
 * is stamped onto {@code reviewer_admin_id} for every decision made through
 * this placeholder. This is a known, accepted gap — v0.5 will need a data
 * migration or simply accept that pre-v0.5 review records carry this
 * sentinel UUID rather than a real admin identity.
 */
@Component
public class PlaceholderAdminAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(PlaceholderAdminAuthFilter.class);

    private static final String ADMIN_TOKEN_HEADER = "X-Admin-Token";

    /** Sentinel UUID stamped on reviewer_admin_id until real admin accounts exist. */
    public static final UUID PLACEHOLDER_ADMIN_ID =
            UUID.fromString("00000000-0000-7000-0000-000000000001");

    public static final String ADMIN_AUTH_ATTRIBUTE = "stash.kyc.isAdminAuthenticated";

    private final String configuredAdminToken;

    public PlaceholderAdminAuthFilter(
            @Value("${stash.kyc.placeholder-admin-token}") String configuredAdminToken) {
        this.configuredAdminToken = configuredAdminToken;
        log.warn("PlaceholderAdminAuthFilter active — NOT real admin auth. " +
                "Replace entirely when v0.5 admin auth ships.");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        if (request.getRequestURI().startsWith("/api/v1/kyc/admin")) {
            String token = request.getHeader(ADMIN_TOKEN_HEADER);
            boolean authenticated = configuredAdminToken.equals(token);
            request.setAttribute(ADMIN_AUTH_ATTRIBUTE, authenticated);

            if (!authenticated) {
                log.warn("Admin endpoint access denied: missing or invalid placeholder token");
            }
        }

        filterChain.doFilter(request, response);
    }
}
