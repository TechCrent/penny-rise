package com.stash.admin.service;

import com.stash.admin.repository.AdminLoginAttemptRepository;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Implements the 5-failures-per-15-minutes lockout with escalating duration.
 *
 * <p><strong>Episode model:</strong> failures are grouped into "episodes" of
 * 5 consecutive failures each, counted since the last successful login (or
 * since account creation if never logged in). Episode 1 triggers a 15-minute
 * lockout. Episode 2 (the next 5 failures, which can only accumulate after
 * episode 1's lockout has been waited out or attempted-through) triggers a
 * 1-hour lockout. Episode 3 and beyond trigger 24 hours.
 *
 * <p>This is a count-based model, not a sliding-window-per-episode model —
 * simpler to reason about and matches "escalating duration" without needing
 * to track episode boundaries explicitly. The failure count resets to zero
 * on any successful login.
 */
@Component
public class AdminLockoutPolicy {

    static final int      FAILURES_PER_EPISODE = 5;
    static final Duration WINDOW               = Duration.ofMinutes(15);

    static final Duration TIER_1_LOCKOUT = Duration.ofMinutes(15);
    static final Duration TIER_2_LOCKOUT = Duration.ofHours(1);
    static final Duration TIER_3_LOCKOUT = Duration.ofHours(24);

    private final AdminLoginAttemptRepository attemptRepo;
    private final Clock                       clock;

    public AdminLockoutPolicy(AdminLoginAttemptRepository attemptRepo, Clock clock) {
        this.attemptRepo = attemptRepo;
        this.clock       = clock;
    }

    /**
     * Returns the lockout expiry instant if the account is currently locked out,
     * or {@code null} if login attempts are currently permitted.
     */
    public Instant lockedUntil(String email) {
        Instant now         = Instant.now(clock);
        Instant lastSuccess = safeLastSuccess(email);

        long totalFailuresSinceLastSuccess =
                attemptRepo.countFailuresSince(email, lastSuccess);

        if (totalFailuresSinceLastSuccess < FAILURES_PER_EPISODE) {
            return null;
        }

        long episode = (totalFailuresSinceLastSuccess + FAILURES_PER_EPISODE - 1)
                / FAILURES_PER_EPISODE;

        Duration lockoutDuration = durationForEpisode(episode);

        Instant windowStart    = now.minus(WINDOW);
        long    failuresInWindow = attemptRepo.countRecentFailures(email, windowStart);

        if (failuresInWindow < FAILURES_PER_EPISODE) {
            return null;
        }

        Instant mostRecentFailure = mostRecentFailureTime(email);
        if (mostRecentFailure == null) return null;

        Instant lockExpiry = mostRecentFailure.plus(lockoutDuration);
        return lockExpiry.isAfter(now) ? lockExpiry : null;
    }

    private Duration durationForEpisode(long episode) {
        if (episode <= 1) return TIER_1_LOCKOUT;
        if (episode == 2) return TIER_2_LOCKOUT;
        return TIER_3_LOCKOUT;
    }

    private Instant safeLastSuccess(String email) {
        try {
            Instant t = attemptRepo.findLastSuccessTime(email);
            return t != null ? t : Instant.EPOCH;
        } catch (Exception e) {
            return Instant.EPOCH;
        }
    }

    private Instant mostRecentFailureTime(String email) {
        return attemptRepo.findMostRecentFailureTime(email);
    }
}
