package com.stash.platform.kyc.api;

import com.stash.platform.kyc.client.KycServiceClient;
import com.stash.platform.user.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * Monolith proxy for customer KYC submission endpoints (v0.2-021).
 *
 * <p>Mobile calls {@code /api/v1/kyc/submissions/**} on the monolith;
 * this controller forwards to kyc-service with the authenticated user id.
 */
@RestController
@RequestMapping("/api/v1/kyc/submissions")
@Tag(
    name = "KYC Proxy",
    description = "Monolith proxy to kyc-service for authenticated customers"
)
public class KycProxyController {

    private final KycServiceClient kycServiceClient;

    public KycProxyController(KycServiceClient kycServiceClient) {
        this.kycServiceClient = kycServiceClient;
    }

    @PostMapping
    @Operation(summary = "Create a KYC submission (proxied to kyc-service)")
    @ApiResponse(responseCode = "201", description = "Submission created")
    @ApiResponse(responseCode = "401", description = "Not authenticated")
    public ResponseEntity<byte[]> createSubmission(
        @RequestBody byte[] body,
        Authentication authentication,
        HttpServletRequest request
    ) {
        UUID userId = userId(authentication);
        return kycServiceClient.forward(
            HttpMethod.POST,
            "/api/v1/kyc/submissions",
            userId,
            body,
            forwardedHeaders(request)
        );
    }

    @GetMapping("/me")
    @Operation(
        summary = "Poll the authenticated user's KYC submission (proxied)"
    )
    @ApiResponse(
        responseCode = "200",
        description = "Submission status returned"
    )
    @ApiResponse(responseCode = "401", description = "Not authenticated")
    @ApiResponse(responseCode = "404", description = "No submission found")
    public ResponseEntity<byte[]> getMySubmission(
        Authentication authentication,
        HttpServletRequest request
    ) {
        UUID userId = userId(authentication);
        return kycServiceClient.forward(
            HttpMethod.GET,
            "/api/v1/kyc/submissions/me",
            userId,
            null,
            forwardedHeaders(request)
        );
    }

    @GetMapping("/{id}")
    @Operation(summary = "Poll a KYC submission by id (proxied)")
    @ApiResponse(
        responseCode = "200",
        description = "Submission status returned"
    )
    @ApiResponse(responseCode = "401", description = "Not authenticated")
    @ApiResponse(responseCode = "404", description = "Submission not found")
    public ResponseEntity<byte[]> getSubmission(
        @PathVariable UUID id,
        Authentication authentication,
        HttpServletRequest request
    ) {
        UUID userId = userId(authentication);
        return kycServiceClient.forward(
            HttpMethod.GET,
            "/api/v1/kyc/submissions/" + id,
            userId,
            null,
            forwardedHeaders(request)
        );
    }

    @PostMapping("/{id}/documents")
    @Operation(
        summary = "Confirm a document upload (proxied, signature forwarded)"
    )
    @ApiResponse(responseCode = "200", description = "Document confirmed")
    @ApiResponse(
        responseCode = "401",
        description = "Not authenticated or invalid signature"
    )
    public ResponseEntity<byte[]> confirmDocument(
        @PathVariable UUID id,
        @RequestHeader("X-Storage-Signature") String signature,
        @RequestBody byte[] body,
        Authentication authentication,
        HttpServletRequest request
    ) {
        UUID userId = userId(authentication);
        HttpHeaders headers = forwardedHeaders(request);
        headers.set("X-Storage-Signature", signature);
        return kycServiceClient.forward(
            HttpMethod.POST,
            "/api/v1/kyc/submissions/" + id + "/documents",
            userId,
            body,
            headers
        );
    }

    private static UUID userId(Authentication authentication) {
        return ((AuthenticatedUser) authentication).getUserId();
    }

    private static HttpHeaders forwardedHeaders(HttpServletRequest request) {
        HttpHeaders headers = new HttpHeaders();
        String hostHeader = request.getHeader("Host");
        headers.set(
            "X-Forwarded-Host",
            hostHeader != null && !hostHeader.isBlank()
                ? hostHeader
                : request.getServerName()
        );
        headers.set("X-Forwarded-Proto", request.getScheme());
        headers.set(
            "X-Forwarded-Port",
            String.valueOf(request.getServerPort())
        );
        return headers;
    }
}
