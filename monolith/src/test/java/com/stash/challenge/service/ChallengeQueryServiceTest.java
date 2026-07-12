package com.stash.challenge.service;

import com.stash.challenge.domain.BadgeEntity;
import com.stash.challenge.domain.SavingsChallengeEntity;
import com.stash.challenge.domain.UserChallengeEntity;
import com.stash.challenge.repository.BadgeRepository;
import com.stash.challenge.repository.SavingsChallengeRepository;
import com.stash.challenge.repository.UserChallengeRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class ChallengeQueryServiceTest {

    private static final UUID USER_ID      = UUID.randomUUID();
    private static final UUID CHALLENGE_ID = UUID.randomUUID();
    private static final UUID BADGE_ID     = UUID.randomUUID();

    private final SavingsChallengeRepository challengeRepository = mock(SavingsChallengeRepository.class);
    private final UserChallengeRepository    userChallengeRepository = mock(UserChallengeRepository.class);
    private final BadgeRepository            badgeRepository = mock(BadgeRepository.class);
    private final ChallengeQueryService      service =
            new ChallengeQueryService(challengeRepository, userChallengeRepository, badgeRepository);

    @Test
    @DisplayName("listForUser returns only active, system-owned challenges, with enrollment attached where present")
    void listForUser_returnsActiveSystemOwnedWithEnrollment() {
        var joinable = challenge(CHALLENGE_ID, true, true);
        var inactive = challenge(UUID.randomUUID(), true, false);
        var custom   = challenge(UUID.randomUUID(), false, true);
        when(challengeRepository.findAll()).thenReturn(List.of(joinable, inactive, custom));

        var enrollment = enrollment(CHALLENGE_ID, "ACTIVE", 1000L);
        when(userChallengeRepository.findByUserId(USER_ID)).thenReturn(List.of(enrollment));

        var badge = badge();
        var badges = List.of(badge);
        when(badgeRepository.findAllById(any())).thenReturn(badges);

        List<com.stash.challenge.api.dto.ChallengeResponse> result = service.listForUser(USER_ID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(CHALLENGE_ID);
        assertThat(result.get(0).enrollment()).isNotNull();
        assertThat(result.get(0).enrollment().status()).isEqualTo("ACTIVE");
        assertThat(result.get(0).enrollment().progressAmount()).isEqualTo(1000L);
        assertThat(result.get(0).badgeCode()).isEqualTo("STARTER_SAVER");
    }

    @Test
    @DisplayName("listForUser: a challenge with no enrollment for this user has a null enrollment (AVAILABLE section)")
    void listForUser_noEnrollmentIsNull() {
        var joinable = challenge(CHALLENGE_ID, true, true);
        var badge = badge();
        when(challengeRepository.findAll()).thenReturn(List.of(joinable));
        when(userChallengeRepository.findByUserId(USER_ID)).thenReturn(List.of());
        when(badgeRepository.findAllById(any())).thenReturn(List.of(badge));

        var result = service.listForUser(USER_ID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).enrollment()).isNull();
    }

    @Test
    @DisplayName("getById returns the challenge with the caller's enrollment attached")
    void getById_returnsChallengeWithEnrollment() {
        var challenge = challenge(CHALLENGE_ID, true, true);
        var enrollment = enrollment(CHALLENGE_ID, "COMPLETED", 20000L);
        var badge = badge();
        when(challengeRepository.findById(CHALLENGE_ID)).thenReturn(Optional.of(challenge));
        when(userChallengeRepository.findByUserId(USER_ID)).thenReturn(List.of(enrollment));
        when(badgeRepository.findById(BADGE_ID)).thenReturn(Optional.of(badge));

        var result = service.getById(USER_ID, CHALLENGE_ID);

        assertThat(result.id()).isEqualTo(CHALLENGE_ID);
        assertThat(result.enrollment().status()).isEqualTo("COMPLETED");
    }

    @Test
    @DisplayName("getById: nonexistent challenge returns 404")
    void getById_notFoundReturns404() {
        when(challengeRepository.findById(CHALLENGE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(USER_ID, CHALLENGE_ID))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    private SavingsChallengeEntity challenge(UUID id, boolean systemOwned, boolean active) {
        var c = mock(SavingsChallengeEntity.class);
        when(c.getId()).thenReturn(id);
        when(c.isSystemOwned()).thenReturn(systemOwned);
        when(c.isActive()).thenReturn(active);
        when(c.getName()).thenReturn("Save GHS 200 in 30 Days");
        when(c.getDescription()).thenReturn("Build a saving habit.");
        when(c.getTargetAmount()).thenReturn(20000L);
        when(c.getTargetDurationDays()).thenReturn(30);
        when(c.getBadgeId()).thenReturn(BADGE_ID);
        return c;
    }

    private UserChallengeEntity enrollment(UUID challengeId, String status, long progressAmount) {
        var e = mock(UserChallengeEntity.class);
        when(e.getChallengeId()).thenReturn(challengeId);
        when(e.getStatus()).thenReturn(status);
        when(e.getProgressAmount()).thenReturn(progressAmount);
        when(e.getStartedAt()).thenReturn(Instant.parse("2026-07-01T00:00:00Z"));
        when(e.getCompletedAt()).thenReturn(null);
        return e;
    }

    private BadgeEntity badge() {
        var b = mock(BadgeEntity.class);
        when(b.getId()).thenReturn(BADGE_ID);
        when(b.getBadgeCode()).thenReturn("STARTER_SAVER");
        when(b.getBadgeName()).thenReturn("Starter Saver");
        when(b.getAssetName()).thenReturn("badge_starter_saver");
        return b;
    }
}
