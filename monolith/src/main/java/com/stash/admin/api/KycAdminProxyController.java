package com.stash.admin.api;

import com.stash.admin.client.KycAdminClient;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Monolith proxy for kyc-service admin endpoints (v0.5 admin console routing).
 *
 * <p>All admin-console calls go through the monolith per the system architecture.
 * The monolith validates the admin JWT ({@code AdminJwtAuthenticationFilter}) and
 * {@link KycAdminClient} forwards it as a Bearer token — kyc-service shares
 * {@code JWT_SIGNING_KEY} and verifies it directly, so {@code reviewer_admin_id}
 * on the far side is the real logged-in admin, not a placeholder sentinel.
 */
@RestController
@RequestMapping("/api/v1/kyc/admin")
public class KycAdminProxyController {

    private final KycAdminClient kycAdminClient;

    public KycAdminProxyController(KycAdminClient kycAdminClient) {
        this.kycAdminClient = kycAdminClient;
    }

    @GetMapping("/queue")
    public ResponseEntity<byte[]> getQueue(HttpServletRequest request) {
        String query = request.getQueryString();
        String path = "/api/v1/kyc/admin/queue" + (query != null ? "?" + query : "");
        // Queue items carry document view URLs — forward host headers so kyc-service
        // builds URLs pointing back at the monolith the admin console can reach.
        return kycAdminClient.forward(HttpMethod.GET, path, extractToken(request), null, forwardedHeaders(request));
    }

    @GetMapping("/submissions/{id}")
    public ResponseEntity<byte[]> getSubmission(@PathVariable UUID id, HttpServletRequest request) {
        // Detail carries document view URLs — forward host headers so kyc-service
        // builds URLs pointing back at the monolith (not kyc-service directly).
        return kycAdminClient.forward(HttpMethod.GET,
                "/api/v1/kyc/admin/submissions/" + id, extractToken(request), null, forwardedHeaders(request));
    }

    @PostMapping("/submissions/{id}/approve")
    public ResponseEntity<byte[]> approveSubmission(@PathVariable UUID id, HttpServletRequest request) {
        return kycAdminClient.forward(HttpMethod.POST,
                "/api/v1/kyc/admin/submissions/" + id + "/approve", extractToken(request), null);
    }

    @PostMapping("/submissions/{id}/reject")
    public ResponseEntity<byte[]> rejectSubmission(@PathVariable UUID id,
                                                    @RequestBody byte[] body,
                                                    HttpServletRequest request) {
        return kycAdminClient.forward(HttpMethod.POST,
                "/api/v1/kyc/admin/submissions/" + id + "/reject", extractToken(request), body, null);
    }

    @PostMapping("/submissions/bulk-approve")
    public ResponseEntity<byte[]> bulkApprove(@RequestBody byte[] body, HttpServletRequest request) {
        return kycAdminClient.forward(HttpMethod.POST,
                "/api/v1/kyc/admin/submissions/bulk-approve", extractToken(request), body, null);
    }

    /**
     * Builds the {@code X-Forwarded-*} headers kyc-service uses to construct
     * signed document view URLs that point back at the monolith (per the
     * architecture rule that all traffic flows through the monolith first).
     * Mirrors {@code KycProxyController#forwardedHeaders} on the customer side.
     */
    private static HttpHeaders forwardedHeaders(HttpServletRequest request) {
        HttpHeaders headers = new HttpHeaders();
        String hostHeader = request.getHeader("Host");
        headers.set("X-Forwarded-Host",
                hostHeader != null && !hostHeader.isBlank() ? hostHeader : request.getServerName());
        headers.set("X-Forwarded-Proto", request.getScheme());
        headers.set("X-Forwarded-Port", String.valueOf(request.getServerPort()));
        return headers;
    }

    private static String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        return (header != null && header.startsWith("Bearer ")) ? header.substring(7) : "";
    }
}
