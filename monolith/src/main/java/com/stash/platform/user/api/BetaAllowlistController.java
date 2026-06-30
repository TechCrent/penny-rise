package com.stash.platform.user.api;

import com.stash.platform.user.service.BetaAllowlistService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * Internal endpoints for managing the beta allowlist.
 * Protected by the InternalServiceAuthFilter — not accessible to end users.
 */
@RestController
@RequestMapping("/internal/beta-allowlist")
public class BetaAllowlistController {

    private final BetaAllowlistService betaAllowlistService;

    public BetaAllowlistController(BetaAllowlistService betaAllowlistService) {
        this.betaAllowlistService = betaAllowlistService;
    }

    public record AddRequest(
            @NotBlank @Email String email,
            @NotBlank String addedBy) {}

    public record AddResponse(String email, String result) {}

    public record StatusResponse(boolean gateEnabled) {}

    @PostMapping
    @ResponseStatus(HttpStatus.OK)
    public AddResponse addEmail(@Valid @RequestBody AddRequest request) {
        betaAllowlistService.addEmail(request.email(), request.addedBy());
        return new AddResponse(request.email().toLowerCase().trim(), "ADDED");
    }

    @DeleteMapping("/{email}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeEmail(@PathVariable String email) {
        betaAllowlistService.removeEmail(email);
    }

    @GetMapping("/status")
    public StatusResponse status() {
        return new StatusResponse(betaAllowlistService.isGateEnabled());
    }
}
