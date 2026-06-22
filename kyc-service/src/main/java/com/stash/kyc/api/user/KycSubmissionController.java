package com.stash.kyc.api.user;

import com.stash.kyc.submission.api.dto.CreateSubmissionRequest;
import com.stash.kyc.submission.api.dto.CreateSubmissionResponse;
import com.stash.kyc.submission.service.KycSubmissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * KYC submission endpoints called by the monolith on behalf of authenticated
 * customers.
 *
 * <p>This service trusts the calling user's identity as forwarded by the
 * monolith — the monolith has already validated the customer JWT and
 * forwards the resolved userId via the {@code X-Authenticated-User-Id}
 * header on internal calls. The KYC Service itself has no customer-facing
 * JWT validation of its own; it lives entirely in the internal trust zone
 * (System Design §10.1) and is unreachable from the public gateway.
 */
@RestController
@RequestMapping("/api/v1/kyc/submissions")
@Tag(name = "KYC Submissions", description = "Customer-facing KYC submission flow")
public class KycSubmissionController {

    private static final String USER_ID_HEADER = "X-Authenticated-User-Id";

    private final KycSubmissionService kycSubmissionService;

    public KycSubmissionController(KycSubmissionService kycSubmissionService) {
        this.kycSubmissionService = kycSubmissionService;
    }

    @PostMapping
    @Operation(
        summary     = "Create a new KYC submission",
        description = "First step of the KYC flow. Returns signed upload URLs " +
                      "for the three required documents.")
    @ApiResponse(responseCode = "201", description = "Submission created")
    @ApiResponse(responseCode = "401", description = "Missing authenticated user header")
    @ApiResponse(responseCode = "409", description = "User already has an active submission")
    @ApiResponse(responseCode = "422", description = "Invalid Ghana Card format")
    public ResponseEntity<CreateSubmissionResponse> createSubmission(
            @Valid @RequestBody CreateSubmissionRequest request,
            @RequestHeader(USER_ID_HEADER) UUID userId) {

        CreateSubmissionResponse response = kycSubmissionService.createSubmission(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
