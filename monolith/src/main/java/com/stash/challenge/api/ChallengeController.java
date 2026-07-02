package com.stash.challenge.api;

import com.stash.challenge.api.dto.JoinChallengeResponse;
import com.stash.challenge.service.ChallengeEnrollmentService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/challenges")
public class ChallengeController {

    private final ChallengeEnrollmentService enrollmentService;

    public ChallengeController(ChallengeEnrollmentService enrollmentService) {
        this.enrollmentService = enrollmentService;
    }

    @PostMapping("/{id}/join")
    public ResponseEntity<JoinChallengeResponse> join(
            @PathVariable UUID id, Authentication authentication) {
        UUID userId = (UUID) authentication.getPrincipal();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(enrollmentService.join(userId, id));
    }
}
