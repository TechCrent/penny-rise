package com.stash.challenge.service;

import com.stash.challenge.domain.SavingsChallengeEntity;
import com.stash.challenge.repository.SavingsChallengeRepository;
import com.stash.challenge.repository.UserChallengeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ChallengeEnrollmentServiceTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-07-01T09:00:00Z"), ZoneOffset.UTC);
    private static final UUID USER_ID      = UUID.randomUUID();
    private static final UUID CHALLENGE_ID = UUID.randomUUID();

    private final SavingsChallengeRepository challengeRepository =
            mock(SavingsChallengeRepository.class);
    private final UserChallengeRepository userChallengeRepository =
            mock(UserChallengeRepository.class);
    private final ChallengeEnrollmentService service =
            new ChallengeEnrollmentService(challengeRepository, userChallengeRepository, FIXED_CLOCK);

    @BeforeEach
    void setUp() {
        when(userChallengeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("happy join creates an ACTIVE enrollment with targetAmount and progressAmount in response")
    void happyJoin() {
        // Resolved before the outer when() to avoid nested-stubbing Mockito error.
        var challenge = systemChallenge(true);
        when(challengeRepository.findById(CHALLENGE_ID)).thenReturn(Optional.of(challenge));
        when(userChallengeRepository.existsActiveEnrollment(USER_ID, CHALLENGE_ID))
                .thenReturn(false);

        var response = service.join(USER_ID, CHALLENGE_ID);

        assertThat(response.status()).isEqualTo("ACTIVE");
        assertThat(response.targetAmount()).isEqualTo(20000L);
        assertThat(response.progressAmount()).isZero();
    }

    @Test
    @DisplayName("already-active enrollment returns 409 CHALLENGE_ALREADY_ACTIVE")
    void alreadyEnrolledReturns409() {
        var challenge = systemChallenge(true);
        when(challengeRepository.findById(CHALLENGE_ID)).thenReturn(Optional.of(challenge));
        when(userChallengeRepository.existsActiveEnrollment(USER_ID, CHALLENGE_ID))
                .thenReturn(true);

        assertThatThrownBy(() -> service.join(USER_ID, CHALLENGE_ID))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    var e = (ResponseStatusException) ex;
                    assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(e.getReason()).contains("CHALLENGE_ALREADY_ACTIVE");
                });
    }

    @Test
    @DisplayName("nonexistent challenge returns 404 CHALLENGE_NOT_FOUND")
    void challengeNotFoundReturns404() {
        when(challengeRepository.findById(CHALLENGE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.join(USER_ID, CHALLENGE_ID))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    @DisplayName("non-system-owned challenge returns 404 — not joinable via this endpoint")
    void customChallengeReturns404() {
        var nonSystemChallenge = mock(SavingsChallengeEntity.class);
        when(nonSystemChallenge.isSystemOwned()).thenReturn(false);
        when(challengeRepository.findById(CHALLENGE_ID))
                .thenReturn(Optional.of(nonSystemChallenge));

        assertThatThrownBy(() -> service.join(USER_ID, CHALLENGE_ID))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    private SavingsChallengeEntity systemChallenge(boolean active) {
        var challenge = mock(SavingsChallengeEntity.class);
        when(challenge.isSystemOwned()).thenReturn(true);
        when(challenge.isActive()).thenReturn(active);
        when(challenge.getTargetAmount()).thenReturn(20000L);
        return challenge;
    }
}
