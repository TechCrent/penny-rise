package com.stash.kyc.api.user;

import com.stash.kyc.submission.api.dto.CreateSubmissionRequest;
import com.stash.kyc.submission.api.dto.CreateSubmissionResponse;
import com.stash.kyc.submission.api.dto.SubmissionStatusResponse;
import com.stash.kyc.submission.service.KycSubmissionService;
import com.stash.kyc.submission.service.KycSubmissionStatusService;
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
    private final KycSubmissionStatusService statusService;

    public KycSubmissionController(KycSubmissionService kycSubmissionService,
                                   KycSubmissionStatusService statusService) {
        this.kycSubmissionService = kycSubmissionService;
        this.statusService = statusService;
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

    @GetMapping("/me")
    @Operation(
        summary     = "Poll the authenticated user's most recent KYC submission",
        description = "Convenience route — no submission ID required. Returns the " +
                      "user's most recent submission regardless of its status.")
    @ApiResponse(responseCode = "200", description = "Submission status returned")
    @ApiResponse(responseCode = "401", description = "Missing authenticated user header")
    @ApiResponse(responseCode = "404", description = "User has no KYC submission")
    public ResponseEntity<SubmissionStatusResponse> getMyStatus(
            @RequestHeader(USER_ID_HEADER) UUID userId) {
        return ResponseEntity.ok(statusService.getMyStatus(userId));
    }

    @GetMapping("/{id}")
    @Operation(
        summary     = "Poll KYC submission status",
        description = "Returns status, timestamps, and rejection reason if applicable. " +
                      "Never returns ghana_card_number or document storage paths. " +
                      "A caller may only fetch their own submission — cross-user " +
                      "access returns 404, not 403, to avoid confirming the submission's existence.")
    @ApiResponse(responseCode = "200", description = "Submission status returned")
    @ApiResponse(responseCode = "401", description = "Missing authenticated user header")
    @ApiResponse(responseCode = "404", description = "Submission not found or not owned by caller")
    public ResponseEntity<SubmissionStatusResponse> getStatus(
            @PathVariable UUID id,
            @RequestHeader(USER_ID_HEADER) UUID userId) {
        return ResponseEntity.ok(statusService.getStatus(id, userId));
    }
}
