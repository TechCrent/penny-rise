package com.stash.admin.api;

import com.stash.admin.client.AuditServiceClient;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Monolith proxy for audit-service admin endpoints (v0.5 admin console routing).
 *
 * <p>All admin-console calls go through the monolith per the system architecture.
 * The monolith validates the admin JWT ({@code AdminJwtAuthenticationFilter}) and
 * {@link AuditServiceClient} forwards it as a Bearer token — audit-service
 * shares {@code JWT_SIGNING_KEY} and verifies it directly.
 */
@RestController
@RequestMapping("/api/v1/admin/audit-log")
public class AuditLogAdminProxyController {

    private final AuditServiceClient auditServiceClient;

    public AuditLogAdminProxyController(AuditServiceClient auditServiceClient) {
        this.auditServiceClient = auditServiceClient;
    }

    @GetMapping
    public ResponseEntity<byte[]> getAuditLog(HttpServletRequest request) {
        String query = request.getQueryString();
        String path = "/api/v1/admin/audit-log" + (query != null ? "?" + query : "");
        return auditServiceClient.forward(HttpMethod.GET, path, extractToken(request), null);
    }

    @GetMapping("/facets")
    public ResponseEntity<byte[]> getAuditLogFacets(HttpServletRequest request) {
        return auditServiceClient.forward(HttpMethod.GET, "/api/v1/admin/audit-log/facets",
                extractToken(request), null);
    }

    private static String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        return (header != null && header.startsWith("Bearer ")) ? header.substring(7) : "";
    }
}
