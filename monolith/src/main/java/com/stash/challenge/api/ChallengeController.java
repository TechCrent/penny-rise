package com.stash.challenge.api;

import com.stash.challenge.api.dto.ChallengeResponse;
import com.stash.challenge.api.dto.JoinChallengeResponse;
import com.stash.challenge.service.ChallengeEnrollmentService;
import com.stash.challenge.service.ChallengeQueryService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/challenges")
public class ChallengeController {

    private final ChallengeEnrollmentService enrollmentService;
    private final ChallengeQueryService      queryService;

    public ChallengeController(ChallengeEnrollmentService enrollmentService,
                                ChallengeQueryService queryService) {
        this.enrollmentService = enrollmentService;
        this.queryService      = queryService;
    }

    @GetMapping
    public List<ChallengeResponse> list(Authentication authentication) {
        UUID userId = (UUID) authentication.getPrincipal();
        return queryService.listForUser(userId);
    }

    @GetMapping("/{id}")
    public ChallengeResponse getById(@PathVariable UUID id, Authentication authentication) {
        UUID userId = (UUID) authentication.getPrincipal();
        return queryService.getById(userId, id);
    }

    @PostMapping("/{id}/join")
    public ResponseEntity<JoinChallengeResponse> join(
            @PathVariable UUID id, Authentication authentication) {
        UUID userId = (UUID) authentication.getPrincipal();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(enrollmentService.join(userId, id));
    }
}
