package com.stash.admin.api;

import com.stash.admin.client.KycAdminClient;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Monolith proxy for kyc-service admin endpoints (v0.5 admin console routing).
 *
 * <p>All admin-console calls go through the monolith per the system architecture.
 * The monolith validates the admin JWT ({@code AdminJwtAuthenticationFilter}), then
 * {@link KycAdminClient} forwards with the placeholder admin token that
 * kyc-service's {@code PlaceholderAdminAuthFilter} expects.
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
        return kycAdminClient.forward(HttpMethod.GET, path, null);
    }

    @GetMapping("/submissions/{id}")
    public ResponseEntity<byte[]> getSubmission(@PathVariable UUID id) {
        return kycAdminClient.forward(HttpMethod.GET,
                "/api/v1/kyc/admin/submissions/" + id, null);
    }

    @PostMapping("/submissions/{id}/approve")
    public ResponseEntity<byte[]> approveSubmission(@PathVariable UUID id) {
        return kycAdminClient.forward(HttpMethod.POST,
                "/api/v1/kyc/admin/submissions/" + id + "/approve", null);
    }

    @PostMapping("/submissions/{id}/reject")
    public ResponseEntity<byte[]> rejectSubmission(@PathVariable UUID id,
                                                    @RequestBody byte[] body) {
        return kycAdminClient.forward(HttpMethod.POST,
                "/api/v1/kyc/admin/submissions/" + id + "/reject", body);
    }
}
