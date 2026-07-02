package com.stash.challenge.service;

import com.stash.challenge.api.dto.JoinChallengeResponse;
import com.stash.challenge.domain.UserChallengeEntity;
import com.stash.challenge.repository.SavingsChallengeRepository;
import com.stash.challenge.repository.UserChallengeRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
public class ChallengeEnrollmentService {

    private final SavingsChallengeRepository challengeRepository;
    private final UserChallengeRepository    userChallengeRepository;
    private final Clock                      clock;

    public ChallengeEnrollmentService(SavingsChallengeRepository challengeRepository,
                                       UserChallengeRepository userChallengeRepository,
                                       Clock clock) {
        this.challengeRepository    = challengeRepository;
        this.userChallengeRepository = userChallengeRepository;
        this.clock                  = clock;
    }

    @Transactional
    public JoinChallengeResponse join(UUID userId, UUID challengeId) {
        var challenge = challengeRepository.findById(challengeId)
                // system_owned=false (custom, v1.5) and is_active=false both 404 here.
                // A deactivated challenge is indistinguishable from nonexistent from
                // the customer's perspective (Overall doc §13.9: admins can deactivate
                // platform-preset challenges).
                .filter(c -> c.isSystemOwned())
                .filter(c -> c.isActive())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "CHALLENGE_NOT_FOUND: No joinable challenge with id " + challengeId));

        if (userChallengeRepository.existsActiveEnrollment(userId, challengeId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "CHALLENGE_ALREADY_ACTIVE: User already has an active enrollment in this challenge.");
        }

        Instant now = Instant.now(clock);
        UserChallengeEntity enrollment;
        try {
            enrollment = userChallengeRepository.save(UserChallengeEntity.create(userId, challengeId, now));
        } catch (DataIntegrityViolationException e) {
            // Race backstop: the TOCTOU window between existsActiveEnrollment and save
            // is closed by V37's partial unique index, not the pre-check above.
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "CHALLENGE_ALREADY_ACTIVE: User already has an active enrollment in this challenge.");
        }

        return new JoinChallengeResponse(
                enrollment.getId(),
                challengeId,
                enrollment.getStatus(),
                challenge.getTargetAmount(),
                enrollment.getProgressAmount(),
                enrollment.getStartedAt());
    }
}
