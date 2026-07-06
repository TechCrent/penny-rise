package com.stash.kyc.api.admin;

import com.stash.kyc.submission.api.dto.*;
import com.stash.kyc.submission.service.KycAdminReviewService;
import com.stash.shared.apierrors.ErrorCode;
import com.stash.shared.apierrors.StashApiException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Admin endpoints for the KYC manual review queue. Called by the admin
 * console (v0.2-031).
 *
 * <p>Auth is a real per-admin JWT, verified by
 * {@link com.stash.kyc.shared.security.AdminJwtAuthenticationFilter} — the
 * same token the monolith issued for the logged-in admin, forwarded as a
 * Bearer token. {@code requireAdmin} returns the actual admin's account id,
 * which is what gets stamped onto {@code reviewer_admin_id}.
 *
 * <p>Endpoint shape: System Design's public API table specifies a single
 * POST .../decide endpoint; this issue's acceptance criteria specify
 * separate /approve and /reject endpoints. Both are exposed — /decide
 * for doc-fidelity, /approve and /reject for issue-fidelity — all three
 * delegate to {@link KycAdminReviewService#decide}.
 */
@RestController
@RequestMapping("/api/v1/kyc/admin")
@Tag(name = "KYC Admin Review", description = "Manual review queue for KYC submissions")
public class KycAdminController {

    private final KycAdminReviewService reviewService;

    public KycAdminController(KycAdminReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @GetMapping("/queue/count")
    @Operation(summary = "Count of submissions currently awaiting manual review")
    @ApiResponse(responseCode = "200", description = "Count returned")
    @ApiResponse(responseCode = "403", description = "Not an authenticated admin")
    public ResponseEntity<AdminQueueCountResponse> getQueueCount(HttpServletRequest request) {
        requireAdmin(request);
        return ResponseEntity.ok(new AdminQueueCountResponse(reviewService.getQueueCount()));
    }

    @GetMapping("/queue")
    @Operation(summary = "List submissions awaiting manual review, oldest first")
    @ApiResponse(responseCode = "200", description = "Queue page returned")
    @ApiResponse(responseCode = "403", description = "Not an authenticated admin")
    public ResponseEntity<AdminQueuePageResponse> getQueue(
            @RequestParam(value = "cursor", required = false) String cursor,
            @RequestParam(value = "pageSize", defaultValue = "20") int pageSize,
            HttpServletRequest request) {
        requireAdmin(request);
        return ResponseEntity.ok(reviewService.getQueue(cursor, pageSize));
    }

    @GetMapping("/submissions/{id}")
    @Operation(summary = "Get full submission detail for manual review")
    @ApiResponse(responseCode = "200", description = "Submission detail returned")
    @ApiResponse(responseCode = "403", description = "Not an authenticated admin")
    @ApiResponse(responseCode = "404", description = "Submission not found")
    public ResponseEntity<AdminSubmissionDetailResponse> getDetail(
            @PathVariable UUID id, HttpServletRequest request) {
        requireAdmin(request);
        return ResponseEntity.ok(reviewService.getDetail(id));
    }

    @PostMapping("/submissions/{id}/approve")
    @Operation(summary = "Approve a submission under manual review")
    @ApiResponse(responseCode = "204", description = "Approved")
    @ApiResponse(responseCode = "403", description = "Not an authenticated admin")
    public ResponseEntity<Void> approve(@PathVariable UUID id, HttpServletRequest request) {
        UUID adminId = requireAdmin(request);
        reviewService.decide(id, KycAdminReviewService.DECIDE_APPROVE, null, adminId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/submissions/{id}/reject")
    @Operation(summary = "Reject a submission under manual review — reason required")
    @ApiResponse(responseCode = "204", description = "Rejected")
    @ApiResponse(responseCode = "403", description = "Not an authenticated admin")
    @ApiResponse(responseCode = "422", description = "Missing reason")
    public ResponseEntity<Void> reject(
            @PathVariable UUID id,
            @RequestBody AdminDecisionRequest request,
            HttpServletRequest httpRequest) {
        UUID adminId = requireAdmin(httpRequest);
        reviewService.decide(id, KycAdminReviewService.DECIDE_REJECT, request.reason(), adminId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/submissions/bulk-approve")
    @Operation(summary = "Approve multiple submissions under manual review at once")
    @ApiResponse(responseCode = "200", description = "Per-submission results returned")
    @ApiResponse(responseCode = "403", description = "Not an authenticated admin")
    public ResponseEntity<BulkActionResultResponse> bulkApprove(
            @RequestBody BulkApproveRequest request, HttpServletRequest httpRequest) {
        UUID adminId = requireAdmin(httpRequest);
        return ResponseEntity.ok(new BulkActionResultResponse(
                reviewService.bulkApprove(request.submissionIds(), adminId)));
    }

    @PostMapping("/submissions/{id}/decide")
    @Operation(summary = "Decide a submission — System Design canonical route")
    @ApiResponse(responseCode = "204", description = "Decided")
    @ApiResponse(responseCode = "403", description = "Not an authenticated admin")
    @ApiResponse(responseCode = "422", description = "Missing reason on REJECT")
    public ResponseEntity<Void> decide(
            @PathVariable UUID id,
            @RequestParam("action") String action,
            @RequestBody(required = false) AdminDecisionRequest request,
            HttpServletRequest httpRequest) {
        UUID adminId = requireAdmin(httpRequest);
        String reason = request != null ? request.reason() : null;
        reviewService.decide(id, action.toUpperCase(), reason, adminId);
        return ResponseEntity.noContent().build();
    }

    private UUID requireAdmin(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof UUID adminId)) {
            throw new StashApiException(
                    ErrorCode.FORBIDDEN,
                    "Admin authentication required.",
                    HttpStatus.FORBIDDEN
            );
        }
        return adminId;
    }
}
