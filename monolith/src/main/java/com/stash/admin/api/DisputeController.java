package com.stash.admin.api;

import com.stash.admin.api.dto.CreateDisputeRequest;
import com.stash.admin.api.dto.CreateDisputeResponse;
import com.stash.admin.service.DisputeService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/disputes")
public class DisputeController {

    private final DisputeService disputeService;

    public DisputeController(DisputeService disputeService) {
        this.disputeService = disputeService;
    }

    @PostMapping
    public ResponseEntity<CreateDisputeResponse> create(@Valid @RequestBody CreateDisputeRequest request,
                                                         Authentication authentication) {
        UUID userId = (UUID) authentication.getPrincipal();
        CreateDisputeResponse response = disputeService.raiseDispute(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
