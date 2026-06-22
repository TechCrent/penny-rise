package com.stash.platform.user.api;

import com.stash.platform.user.api.dto.UpdateUserProfileRequest;
import com.stash.platform.user.api.dto.UserProfileResponse;
import com.stash.platform.user.security.AuthenticatedUser;
import com.stash.platform.user.service.UserProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * Endpoints for the authenticated user to view and update their own profile.
 * All endpoints require a valid JWT — enforced by SecurityConfig's
 * .anyRequest().authenticated() rule (these paths are not in the public allowlist).
 */
@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "User Profile", description = "Authenticated user's own profile")
public class UserController {

    private final UserProfileService userProfileService;

    public UserController(UserProfileService userProfileService) {
        this.userProfileService = userProfileService;
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
    @ApiResponse(responseCode = "422", description = "Validation error (e.g. malformed phone)")
    public ResponseEntity<UserProfileResponse> updateMe(
            @Valid @RequestBody UpdateUserProfileRequest request,
            Authentication authentication) {
        AuthenticatedUser authUser = (AuthenticatedUser) authentication;
        return ResponseEntity.ok(
                userProfileService.updateProfile(authUser.getUserId(), request));
    }
}
