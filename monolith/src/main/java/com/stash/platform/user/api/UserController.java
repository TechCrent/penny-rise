package com.stash.platform.user.api;

import com.stash.platform.user.api.dto.ChangePasswordRequest;
import com.stash.platform.user.api.dto.DeletionRequestResponse;
import com.stash.platform.user.api.dto.GdprDataExportResponse;
import com.stash.platform.user.api.dto.UpdateUserProfileRequest;
import com.stash.platform.user.api.dto.UserProfileResponse;
import com.stash.platform.user.domain.DeletionRequest;
import com.stash.platform.user.security.AuthenticatedUser;
import com.stash.platform.user.service.ChangePasswordService;
import com.stash.platform.user.service.DeletionRequestService;
import com.stash.platform.user.service.UserDataExportService;
import com.stash.platform.user.service.UserProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * Authenticated user profile and account lifecycle endpoints.
 * Per Folder Structure doc: api/ layer only; no business logic here.
 */
@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "Users", description = "Authenticated user profile and account management")
public class UserController {

    private final UserProfileService userProfileService;
    private final DeletionRequestService deletionRequestService;
    private final ChangePasswordService changePasswordService;
    private final UserDataExportService userDataExportService;

    public UserController(UserProfileService userProfileService,
                          DeletionRequestService deletionRequestService,
                          ChangePasswordService changePasswordService,
                          UserDataExportService userDataExportService) {
        this.userProfileService = userProfileService;
        this.deletionRequestService = deletionRequestService;
        this.changePasswordService = changePasswordService;
        this.userDataExportService = userDataExportService;
    }

    @GetMapping("/me")
    @Operation(
        summary     = "Get the authenticated user's profile",
        description = "Returns the current user's profile. Never includes " +
                      "password_hash or ghana_card_number.")
    @ApiResponse(responseCode = "200", description = "Profile returned")
    @ApiResponse(responseCode = "401", description = "Not authenticated")
    public ResponseEntity<UserProfileResponse> getMe(Authentication authentication) {
        AuthenticatedUser authUser = (AuthenticatedUser) authentication;
        return ResponseEntity.ok(userProfileService.getProfile(authUser.getUserId()));
    }

    @PatchMapping("/me")
    @Operation(
        summary     = "Update the authenticated user's profile",
        description = "Allows updating display_name and phone only. " +
                      "Phone is set once and frozen for v0.2 — subsequent " +
                      "attempts to change an already-set phone return 409. " +
                      "Any other fields submitted by the client are structurally " +
                      "impossible to set through this endpoint.")
    @ApiResponse(responseCode = "200", description = "Profile updated")
    @ApiResponse(responseCode = "401", description = "Not authenticated")
    @ApiResponse(responseCode = "409", description = "Phone already set (v0.2 limitation)")
    @ApiResponse(responseCode = "400", description = "Validation error (e.g. malformed phone)")
    public ResponseEntity<UserProfileResponse> updateMe(
            @Valid @RequestBody UpdateUserProfileRequest request,
            Authentication authentication) {
        AuthenticatedUser authUser = (AuthenticatedUser) authentication;
        return ResponseEntity.ok(
                userProfileService.updateProfile(authUser.getUserId(), request));
    }

    @PostMapping("/me/change-password")
    @Operation(
        summary     = "Change the authenticated user's password",
        description = "Requires the current password. Distinct from the " +
                      "unauthenticated forgot/reset-password flow.")
    @ApiResponse(responseCode = "204", description = "Password changed")
    @ApiResponse(responseCode = "401", description = "Not authenticated, or current password incorrect")
    @ApiResponse(responseCode = "400", description = "Validation error (e.g. weak new password)")
    public ResponseEntity<Void> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            Authentication authentication) {
        AuthenticatedUser authUser = (AuthenticatedUser) authentication;
        changePasswordService.changePassword(authUser.getUserId(), request);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me/data-export")
    @Operation(
        summary     = "Download all of the authenticated user's data",
        description = "GDPR-style data portability bundle: profile, vaults, susu " +
                      "memberships, and full transaction history. Excludes raw KYC " +
                      "document images and password hashes.")
    @ApiResponse(responseCode = "200", description = "Data export returned")
    @ApiResponse(responseCode = "401", description = "Not authenticated")
    public ResponseEntity<GdprDataExportResponse> exportMyData(Authentication authentication) {
        AuthenticatedUser authUser = (AuthenticatedUser) authentication;
        return ResponseEntity.ok(userDataExportService.export(authUser.getUserId()));
    }

    @PostMapping("/me/deletion-request")
    @Operation(
        summary     = "Request account deletion",
        description = "Submits a 30-day account deletion request. The user can " +
                      "continue using the app normally during the cool-off period. " +
                      "Actual deletion executes via the v0.5 cleanup job.")
    @ApiResponse(responseCode = "201", description = "Deletion request submitted")
    @ApiResponse(responseCode = "401", description = "Not authenticated")
    @ApiResponse(responseCode = "409", description = "A pending request already exists")
    public ResponseEntity<DeletionRequestResponse> requestDeletion(
            Authentication authentication) {
        AuthenticatedUser authUser = (AuthenticatedUser) authentication;
        DeletionRequest request = deletionRequestService.submit(authUser.getUserId());

        return ResponseEntity.status(HttpStatus.CREATED).body(new DeletionRequestResponse(
                request.getId(),
                request.getStatus(),
                request.getSubmittedAt(),
                request.getScheduledCompletionAt(),
                request.getBlockersAtSubmission()
        ));
    }

    @DeleteMapping("/me/deletion-request")
    @Operation(
        summary     = "Cancel a pending account deletion request",
        description = "Cancels the user's active PENDING deletion request, if any.")
    @ApiResponse(responseCode = "204", description = "Deletion request cancelled")
    @ApiResponse(responseCode = "401", description = "Not authenticated")
    @ApiResponse(responseCode = "404", description = "No pending request found")
    public ResponseEntity<Void> cancelDeletion(Authentication authentication) {
        AuthenticatedUser authUser = (AuthenticatedUser) authentication;
        deletionRequestService.cancel(authUser.getUserId());
        return ResponseEntity.noContent().build();
    }
}
