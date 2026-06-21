package com.stash.platform.user.api;

import com.stash.platform.user.api.dto.LogoutRequest;
import com.stash.platform.user.security.AuthenticatedUser;
import com.stash.platform.user.service.LogoutService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * Authentication endpoints — registration, login, token management.
 * Per Folder Structure doc: api/ layer only; no business logic here.
 */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "User registration and session management")
public class AuthController {

    private final LogoutService logoutService;

    public AuthController(LogoutService logoutService) {
        this.logoutService = logoutService;
    }

    @PostMapping("/logout")
    @Operation(
        summary     = "Log out — revoke a refresh token",
        description = "Revokes the supplied refresh token. With all_devices=true, "
                    + "revokes every active session for the authenticated user. "
                    + "Requires a valid access token.")
    @ApiResponse(responseCode = "204", description = "Logged out successfully")
    @ApiResponse(responseCode = "403", description = "Token belongs to a different user")
    public ResponseEntity<Void> logout(
            @Valid @RequestBody LogoutRequest request,
            Authentication authentication) {
        var authUser = (AuthenticatedUser) authentication;
        logoutService.logout(request.refreshToken(), request.allDevices(), authUser.getUserId());
        return ResponseEntity.noContent().build();
    }
}
