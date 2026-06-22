package com.stash.platform.user.api;

import com.stash.platform.user.api.dto.RefreshRequest;
import com.stash.platform.user.api.dto.RefreshResponse;
import com.stash.platform.user.service.TokenRefreshService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
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

    private final TokenRefreshService tokenRefreshService;

    public AuthController(TokenRefreshService tokenRefreshService) {
        this.tokenRefreshService = tokenRefreshService;
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
}
