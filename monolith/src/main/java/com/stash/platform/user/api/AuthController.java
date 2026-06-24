package com.stash.platform.user.api;

import com.stash.platform.user.api.dto.ForgotPasswordRequest;
import com.stash.platform.user.api.dto.ForgotPasswordResponse;
import com.stash.platform.user.api.dto.LoginRequest;
import com.stash.platform.user.api.dto.LoginResponse;
import com.stash.platform.user.api.dto.LogoutRequest;
import com.stash.platform.user.api.dto.RefreshRequest;
import com.stash.platform.user.api.dto.RefreshResponse;
import com.stash.platform.user.api.dto.ResendVerificationRequest;
import com.stash.platform.user.api.dto.ResetPasswordRequest;
import com.stash.platform.user.api.dto.SignupRequest;
import com.stash.platform.user.api.dto.SignupResponse;
import com.stash.platform.user.security.AuthenticatedUser;
import com.stash.platform.user.service.EmailVerificationService;
import com.stash.platform.user.service.ForgotPasswordService;
import com.stash.platform.user.service.LoginService;
import com.stash.platform.user.service.LogoutService;
import com.stash.platform.user.service.ResetPasswordService;
import com.stash.platform.user.service.SignupService;
import com.stash.platform.user.service.TokenRefreshService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
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

    private final SignupService signupService;
    private final LoginService loginService;
    private final EmailVerificationService emailVerificationService;
    private final TokenRefreshService tokenRefreshService;
    private final LogoutService logoutService;
    private final ForgotPasswordService forgotPasswordService;
    private final ResetPasswordService resetPasswordService;

    public AuthController(SignupService signupService,
                          LoginService loginService,
                          EmailVerificationService emailVerificationService,
                          TokenRefreshService tokenRefreshService,
                          LogoutService logoutService,
                          ForgotPasswordService forgotPasswordService,
                          ResetPasswordService resetPasswordService) {
        this.signupService = signupService;
        this.loginService = loginService;
        this.emailVerificationService = emailVerificationService;
        this.tokenRefreshService = tokenRefreshService;
        this.logoutService = logoutService;
        this.forgotPasswordService = forgotPasswordService;
        this.resetPasswordService = resetPasswordService;
    }

    @PostMapping("/signup")
    @Operation(
        summary     = "Register a new user account",
        description = "Creates a new account. Email verification is required before login.")
    @ApiResponse(responseCode = "201", description = "Account created; verification email sent")
    @ApiResponse(responseCode = "409", description = "Email already registered")
    @ApiResponse(responseCode = "400", description = "Validation error")
    public ResponseEntity<SignupResponse> signup(
            @Valid @RequestBody SignupRequest request) {
        SignupResponse response = signupService.signup(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    @Operation(
        summary     = "Authenticate a user",
        description = "Returns access token + refresh token on success. "
                    + "Rate-limited: 5 consecutive failures within 15 minutes triggers a 1-hour lockout.")
    @ApiResponse(responseCode = "200",  description = "Login successful")
    @ApiResponse(responseCode = "401",  description = "Invalid credentials")
    @ApiResponse(responseCode = "403",  description = "Email not verified or account suspended")
    @ApiResponse(responseCode = "429",  description = "Account locked out")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {
        String ipAddress = httpRequest.getRemoteAddr();
        LoginResponse response = loginService.login(request, ipAddress);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/refresh")
    @Operation(
        summary     = "Exchange a refresh token for a new token pair",
        description = "Rotates the refresh token on every call. " +
                      "Replay detection revokes the entire token chain on a replay attack.")
    @ApiResponse(responseCode = "200", description = "New token pair issued")
    @ApiResponse(responseCode = "401", description = "Token expired or invalid")
    public ResponseEntity<RefreshResponse> refresh(
            @Valid @RequestBody RefreshRequest request,
            HttpServletRequest httpRequest) {
        String ip = httpRequest.getRemoteAddr();
        return ResponseEntity.ok(tokenRefreshService.refresh(request, ip));
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

    @PostMapping("/reset-password")
    @Operation(
        summary     = "Complete a password reset",
        description = "Validates the token, sets the new password, and revokes " +
                      "all active sessions for the user as a security measure.")
    @ApiResponse(responseCode = "204", description = "Password reset successfully")
    @ApiResponse(responseCode = "404", description = "Token not found")
    @ApiResponse(responseCode = "409", description = "Token already used")
    @ApiResponse(responseCode = "410", description = "Token expired")
    @ApiResponse(responseCode = "400", description = "New password fails strength validation")
    public ResponseEntity<Void> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request) {
        resetPasswordService.resetPassword(request.token(), request.newPassword());
        return ResponseEntity.noContent().build();
    }
}
