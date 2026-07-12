package com.stash.challenge.service;

import com.stash.challenge.api.dto.ChallengeResponse;
import com.stash.challenge.domain.BadgeEntity;
import com.stash.challenge.domain.SavingsChallengeEntity;
import com.stash.challenge.domain.UserChallengeEntity;
import com.stash.challenge.repository.BadgeRepository;
import com.stash.challenge.repository.SavingsChallengeRepository;
import com.stash.challenge.repository.UserChallengeRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Backs GET /api/v1/challenges and GET /api/v1/challenges/{id} — previously
 * missing entirely (only POST /{id}/join existed). The mobile app's
 * fetchChallenges() call 404'd on every request as a result, surfacing as
 * "Couldn't load challenges" regardless of whether any challenges existed.
 */
@Service
public class ChallengeQueryService {

    private final SavingsChallengeRepository challengeRepository;
    private final UserChallengeRepository    userChallengeRepository;
    private final BadgeRepository            badgeRepository;

    public ChallengeQueryService(SavingsChallengeRepository challengeRepository,
                                  UserChallengeRepository userChallengeRepository,
                                  BadgeRepository badgeRepository) {
        this.challengeRepository     = challengeRepository;
        this.userChallengeRepository = userChallengeRepository;
        this.badgeRepository         = badgeRepository;
    }

    @Transactional(readOnly = true)
    public List<ChallengeResponse> listForUser(UUID userId) {
        List<SavingsChallengeEntity> challenges = challengeRepository.findAll().stream()
                .filter(SavingsChallengeEntity::isSystemOwned)
                .filter(SavingsChallengeEntity::isActive)
                .toList();

        Map<UUID, UserChallengeEntity> enrollmentByChallengeId =
                userChallengeRepository.findByUserId(userId).stream()
                        .collect(Collectors.toMap(UserChallengeEntity::getChallengeId,
                                Function.identity(), (a, b) -> a));

        Map<UUID, BadgeEntity> badgesById = badgeRepository
                .findAllById(challenges.stream().map(SavingsChallengeEntity::getBadgeId).toList())
                .stream()
                .collect(Collectors.toMap(BadgeEntity::getId, Function.identity()));

        return challenges.stream()
                .map(c -> toResponse(c, enrollmentByChallengeId.get(c.getId()), badgesById.get(c.getBadgeId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public ChallengeResponse getById(UUID userId, UUID challengeId) {
        SavingsChallengeEntity challenge = challengeRepository.findById(challengeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "CHALLENGE_NOT_FOUND: No challenge with id " + challengeId));

        UserChallengeEntity enrollment = userChallengeRepository.findByUserId(userId).stream()
                .filter(uc -> uc.getChallengeId().equals(challengeId))
                .findFirst()
                .orElse(null);

        BadgeEntity badge = challenge.getBadgeId() != null
                ? badgeRepository.findById(challenge.getBadgeId()).orElse(null)
                : null;

        return toResponse(challenge, enrollment, badge);
    }

    private ChallengeResponse toResponse(SavingsChallengeEntity c, UserChallengeEntity enrollment, BadgeEntity badge) {
        ChallengeResponse.Enrollment enrollmentDto = enrollment == null ? null
                : new ChallengeResponse.Enrollment(
                        enrollment.getStatus(),
                        enrollment.getProgressAmount(),
                        enrollment.getStartedAt(),
                        enrollment.getCompletedAt());

        return new ChallengeResponse(
                c.getId(),
                c.getName(),
                c.getDescription(),
                c.getTargetAmount(),
                c.getTargetDurationDays(),
                badge != null ? badge.getBadgeCode()  : null,
                badge != null ? badge.getBadgeName()  : null,
                badge != null ? badge.getAssetName()  : null,
                enrollmentDto);
    }
}
