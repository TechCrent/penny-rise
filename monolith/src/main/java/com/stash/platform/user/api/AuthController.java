package com.stash.platform.user.api;

import com.stash.platform.user.api.dto.ResetPasswordRequest;
import com.stash.platform.user.service.ResetPasswordService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Authentication endpoints — registration, login, token management.
 * Per Folder Structure doc: api/ layer only; no business logic here.
 */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "User registration and session management")
public class AuthController {

    private final ResetPasswordService resetPasswordService;

    public AuthController(ResetPasswordService resetPasswordService) {
        this.resetPasswordService = resetPasswordService;
    }

    @PostMapping("/reset-password")
    @Operation(
        summary     = "Complete a password reset",
        description = "Validates the token, sets the new password, and revokes " +
                      "all active sessions for the user as a security measure.")
    @ApiResponse(responseCode = "204", description = "Password reset successfully")
    @ApiResponse(responseCode = "404", description = "Token not found")
    @ApiResponse(responseCode = "409", description = "Token already used")
    @ApiResponse(responseCode = "410", description = "Token expired")
    @ApiResponse(responseCode = "422", description = "New password fails strength validation")
    public ResponseEntity<Void> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request) {
        resetPasswordService.resetPassword(request.token(), request.newPassword());
        return ResponseEntity.noContent().build();
    }
}
