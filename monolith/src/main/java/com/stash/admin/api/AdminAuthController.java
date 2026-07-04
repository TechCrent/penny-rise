package com.stash.admin.api;

import com.stash.admin.api.dto.AdminLoginRequest;
import com.stash.admin.api.dto.AdminLoginResponse;
import com.stash.admin.service.AdminAuthService;
import com.stash.admin.service.AdminJwtService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/auth")
public class AdminAuthController {

    private final AdminAuthService authService;
    private final AdminJwtService  jwtService;

    public AdminAuthController(AdminAuthService authService, AdminJwtService jwtService) {
        this.authService = authService;
        this.jwtService  = jwtService;
    }

    @PostMapping("/login")
    public AdminLoginResponse login(@Valid @RequestBody AdminLoginRequest request,
                                    HttpServletRequest httpRequest) {
        String sourceIp = resolveClientIp(httpRequest);
        var result = authService.login(request.email(), request.password(), sourceIp);
        return new AdminLoginResponse(
                result.accessToken(), result.refreshToken(), result.accountType(),
                (int) jwtService.getAccessTokenTtl().toSeconds());
    }

    @PostMapping("/refresh")
    public AdminLoginResponse refresh(@RequestHeader("X-Refresh-Token") String refreshToken,
                                      HttpServletRequest httpRequest) {
        String sourceIp = resolveClientIp(httpRequest);
        var result = authService.refresh(refreshToken, sourceIp);
        return new AdminLoginResponse(
                result.accessToken(), result.refreshToken(), result.accountType(),
                (int) jwtService.getAccessTokenTtl().toSeconds());
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
