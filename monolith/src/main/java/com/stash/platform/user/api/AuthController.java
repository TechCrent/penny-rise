package com.stash.platform.user.api;

import com.stash.platform.user.api.dto.ResendVerificationRequest;
import com.stash.platform.user.service.EmailVerificationService;
import com.stash.platform.user.api.dto.SignupRequest;
import com.stash.platform.user.api.dto.SignupResponse;
import com.stash.platform.user.service.SignupService;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "User registration and session management")
public class AuthController {

    private final EmailVerificationService emailVerificationService;

    public AuthController(EmailVerificationService emailVerificationService) {
        this.emailVerificationService = emailVerificationService;
    }

    @GetMapping("/verify-email")
    @Operation(
        summary     = "Verify email address",
        description = "Consumes the token from the verification link. " +
                      "Sets email_verified_at on the user.")
    @ApiResponse(responseCode = "200", description = "Email verified successfully")
    @ApiResponse(responseCode = "404", description = "Token not found")
    @ApiResponse(responseCode = "409", description = "Token already used")
    @ApiResponse(responseCode = "410", description = "Token expired")
    public ResponseEntity<Void> verifyEmail(@RequestParam String token) {
        emailVerificationService.verify(token);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/resend-verification")
    @Operation(
        summary     = "Resend verification email",
        description = "Issues a new verification token and dispatches a new email. " +
                      "Rate-limited to 3 requests per hour per user.")
    @ApiResponse(responseCode = "204", description = "Email sent (or address not found — silent)")
    @ApiResponse(responseCode = "409", description = "Email already verified")
    @ApiResponse(responseCode = "429", description = "Rate limit exceeded")
    public ResponseEntity<Void> resendVerification(
            @Valid @RequestBody ResendVerificationRequest request) {
        emailVerificationService.resend(request.email());
        return ResponseEntity.noContent().build();
    }
}
    private final SignupService signupService;

    public AuthController(SignupService signupService) {
        this.signupService = signupService;
    }

    @PostMapping("/signup")
    @Operation(
        summary     = "Register a new user account",
        description = "Creates a new account. Email verification is required before login.")
    @ApiResponse(responseCode = "201", description = "Account created; verification email sent")
    @ApiResponse(responseCode = "409", description = "Email already registered")
    @ApiResponse(responseCode = "422", description = "Validation error")
    public ResponseEntity<SignupResponse> signup(
            @Valid @RequestBody SignupRequest request) {
        SignupResponse response = signupService.signup(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
