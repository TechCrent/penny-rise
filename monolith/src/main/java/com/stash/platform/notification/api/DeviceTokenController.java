package com.stash.platform.notification.api;

import com.stash.platform.notification.api.dto.RegisterDeviceTokenRequest;
import com.stash.platform.notification.service.DeviceTokenRegistrationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/devices")
public class DeviceTokenController {

    private final DeviceTokenRegistrationService registrationService;

    public DeviceTokenController(DeviceTokenRegistrationService registrationService) {
        this.registrationService = registrationService;
    }

    @PostMapping("/register")
    public ResponseEntity<Void> register(@Valid @RequestBody RegisterDeviceTokenRequest request,
                                          Authentication authentication) {
        // Customer JwtAuthenticationFilter sets the user's UUID as the principal,
        // consistent with all other authenticated endpoints in the monolith.
        UUID userId = (UUID) authentication.getPrincipal();
        registrationService.register(userId, request.token(), request.platform());
        return ResponseEntity.noContent().build();
    }
}
