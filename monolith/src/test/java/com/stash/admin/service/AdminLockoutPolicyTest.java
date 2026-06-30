package com.stash.admin.service;

import com.stash.admin.repository.AdminLoginAttemptRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AdminLockoutPolicyTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-06-29T10:00:00Z"), ZoneOffset.UTC);

    private final AdminLoginAttemptRepository attemptRepo =
            mock(AdminLoginAttemptRepository.class);

    private final AdminLockoutPolicy policy =
            new AdminLockoutPolicy(attemptRepo, FIXED_CLOCK);

    private static final String EMAIL = "admin@stash.app";

    @BeforeEach
    void setUp() {
        when(attemptRepo.findLastSuccessTime(EMAIL)).thenReturn(null);
    }

    @Test
    @DisplayName("fewer than 5 failures: not locked")
    void below_threshold_not_locked() {
        when(attemptRepo.countFailuresSince(eq(EMAIL), any())).thenReturn(4L);
        when(attemptRepo.countRecentFailures(eq(EMAIL), any())).thenReturn(4L);

        assertThat(policy.lockedUntil(EMAIL)).isNull();
    }

    @Test
    @DisplayName("exactly 5 failures within 15 min: tier-1 lockout (15 min)")
    void five_failures_triggers_tier_1() {
        when(attemptRepo.countFailuresSince(eq(EMAIL), any())).thenReturn(5L);
        when(attemptRepo.countRecentFailures(eq(EMAIL), any())).thenReturn(5L);
        when(attemptRepo.findMostRecentFailureTime(EMAIL))
                .thenReturn(Instant.parse("2026-06-29T09:58:00Z")); // 2 min ago

        Instant lockedUntil = policy.lockedUntil(EMAIL);

        assertThat(lockedUntil).isNotNull();
        // 09:58 + 15 min = 10:13, which is after now (10:00) -> still locked
        assertThat(lockedUntil).isEqualTo(Instant.parse("2026-06-29T10:13:00Z"));
    }

    @Test
    @DisplayName("5 failures, lockout window has passed: not locked")
    void tier_1_lockout_expired() {
        when(attemptRepo.countFailuresSince(eq(EMAIL), any())).thenReturn(5L);
        when(attemptRepo.countRecentFailures(eq(EMAIL), any())).thenReturn(0L);
        when(attemptRepo.findMostRecentFailureTime(EMAIL))
                .thenReturn(Instant.parse("2026-06-29T09:00:00Z")); // 1h ago

        assertThat(policy.lockedUntil(EMAIL)).isNull();
    }

    @Test
    @DisplayName("10 failures since last success: tier-2 lockout (1 hour)")
    void ten_failures_triggers_tier_2() {
        when(attemptRepo.countFailuresSince(eq(EMAIL), any())).thenReturn(10L);
        when(attemptRepo.countRecentFailures(eq(EMAIL), any())).thenReturn(5L);
        when(attemptRepo.findMostRecentFailureTime(EMAIL))
                .thenReturn(Instant.parse("2026-06-29T09:55:00Z"));

        Instant lockedUntil = policy.lockedUntil(EMAIL);

        assertThat(lockedUntil).isEqualTo(Instant.parse("2026-06-29T10:55:00Z")); // +1h
    }

    @Test
    @DisplayName("15+ failures since last success: tier-3 lockout (24 hours)")
    void fifteen_failures_triggers_tier_3() {
        when(attemptRepo.countFailuresSince(eq(EMAIL), any())).thenReturn(15L);
        when(attemptRepo.countRecentFailures(eq(EMAIL), any())).thenReturn(5L);
        when(attemptRepo.findMostRecentFailureTime(EMAIL))
                .thenReturn(Instant.parse("2026-06-29T09:55:00Z"));

        Instant lockedUntil = policy.lockedUntil(EMAIL);

        assertThat(lockedUntil).isEqualTo(Instant.parse("2026-06-30T09:55:00Z")); // +24h
    }

    @Test
    @DisplayName("successful login resets the failure count window")
    void success_resets_window() {
        Instant lastSuccess = Instant.parse("2026-06-29T09:50:00Z");
        when(attemptRepo.findLastSuccessTime(EMAIL)).thenReturn(lastSuccess);
        when(attemptRepo.countFailuresSince(eq(EMAIL), eq(lastSuccess))).thenReturn(2L);

        assertThat(policy.lockedUntil(EMAIL)).isNull();

        verify(attemptRepo).countFailuresSince(EMAIL, lastSuccess);
    }
}
