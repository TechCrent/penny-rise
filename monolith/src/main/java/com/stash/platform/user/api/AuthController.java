package com.stash.platform.user.api;

import com.stash.platform.user.api.dto.ForgotPasswordRequest;
import com.stash.platform.user.api.dto.ForgotPasswordResponse;
import com.stash.platform.user.service.ForgotPasswordService;
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

    private final ForgotPasswordService forgotPasswordService;

    public AuthController(ForgotPasswordService forgotPasswordService) {
        this.forgotPasswordService = forgotPasswordService;
    }

    @PostMapping("/forgot-password")
    @Operation(
        summary     = "Request a password reset",
        description = "Always returns 200 with a generic message regardless of "
                    + "whether the email is registered — prevents enumeration.")
    @ApiResponse(responseCode = "200", description = "Request processed (generic response)")
    public ResponseEntity<ForgotPasswordResponse> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request) {
        forgotPasswordService.requestReset(request.email());
        return ResponseEntity.ok(new ForgotPasswordResponse(
                "If an account with that email exists, a password reset link has been sent."
        ));
    }
}
