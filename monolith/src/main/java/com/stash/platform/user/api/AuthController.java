package com.stash.platform.user.api;

import com.stash.platform.user.api.dto.LoginRequest;
import com.stash.platform.user.api.dto.LoginResponse;
import com.stash.platform.user.api.dto.SignupRequest;
import com.stash.platform.user.api.dto.SignupResponse;
import com.stash.platform.user.service.LoginService;
import com.stash.platform.user.service.SignupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
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

    private final SignupService signupService;
    private final LoginService loginService;

    public AuthController(SignupService signupService, LoginService loginService) {
        this.signupService = signupService;
        this.loginService  = loginService;
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
}
