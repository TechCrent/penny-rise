package com.stash.platform.vault.service;

import com.stash.platform.user.domain.User;
import com.stash.platform.user.repository.UserRepository;
import com.stash.platform.vault.domain.EarlyExitRequestEntity;
import com.stash.platform.vault.repository.EarlyExitRequestRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EarlyExitReleaseWorkerTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-06-24T10:00:00Z"), ZoneOffset.UTC);

    private final EarlyExitRequestRepository requestRepo = Mockito.mock(EarlyExitRequestRepository.class);
    private final UserRepository             userRepo    = Mockito.mock(UserRepository.class);
    private final EarlyExitReleaseProcessor  processor    = Mockito.mock(EarlyExitReleaseProcessor.class);
    private final EarlyExitReleaseMetrics    metrics      = Mockito.mock(EarlyExitReleaseMetrics.class);

    private final EarlyExitReleaseWorker worker =
            new EarlyExitReleaseWorker(requestRepo, userRepo, processor, metrics, FIXED_CLOCK);

    private static final UUID VAULT_ID   = UUID.randomUUID();
    private static final UUID USER_ID    = UUID.randomUUID();
    private static final UUID REQUEST_ID = UUID.randomUUID();

    // ── The bug fix: MoMo details come from the request row, not user.getPhone() ──

    @Test
    @DisplayName("processor receives MoMo details from the request row, not the user profile")
    void momo_details_sourced_from_request_row() {
        EarlyExitRequestEntity request = pendingRequest("0247654321", "vodafone");
        when(requestRepo.findDueRequests(any(), anyInt())).thenReturn(List.of(request));
        when(userRepo.findById(USER_ID)).thenReturn(Optional.of(userWithPhone("0200000000")));

        worker.processReleases();

        verify(processor).process(
                eq(request),
                eq("0247654321"),   // from the request row
                eq("vodafone"),    // from the request row
                eq("Akua Mensah"),
                eq("akua@stash.test"),
                anyString()
        );
        verify(metrics, never()).recordP0Alert();
    }

    @Test
    @DisplayName("user's phone number is never used as the MoMo destination, even if different")
    void user_phone_not_used_as_momo_fallback() {
        EarlyExitRequestEntity request = pendingRequest("0247654321", "vodafone");
        when(requestRepo.findDueRequests(any(), anyInt())).thenReturn(List.of(request));
        // User's registered phone deliberately differs from the request's MoMo number.
        when(userRepo.findById(USER_ID)).thenReturn(Optional.of(userWithPhone("0299999999")));

        worker.processReleases();

        ArgumentCaptor<String> momoCaptor = ArgumentCaptor.forClass(String.class);
        verify(processor).process(any(), momoCaptor.capture(), any(), any(), any(), any());
        assertThat(momoCaptor.getValue()).isEqualTo("0247654321");
        assertThat(momoCaptor.getValue()).isNotEqualTo("0299999999");
    }

    // ── Defensive paths ──────────────────────────────────────────────────────

    @Test
    @DisplayName("no due requests: no-op")
    void no_due_requests_is_noop() {
        when(requestRepo.findDueRequests(any(), anyInt())).thenReturn(List.of());

        worker.processReleases();

        verifyNoInteractions(processor, userRepo);
    }

    @Test
    @DisplayName("user not found: P0 alert, processor not called")
    void user_not_found_emits_p0_alert() {
        EarlyExitRequestEntity request = pendingRequest("0247654321", "vodafone");
        when(requestRepo.findDueRequests(any(), anyInt())).thenReturn(List.of(request));
        when(userRepo.findById(USER_ID)).thenReturn(Optional.empty());

        worker.processReleases();

        verify(metrics).recordP0Alert();
        verifyNoInteractions(processor);
    }

    @Test
    @DisplayName("blank MoMo number on a pre-migration row: P0 alert, processor not called")
    void blank_momo_number_emits_p0_alert() {
        EarlyExitRequestEntity request = pendingRequest("", "");
        when(requestRepo.findDueRequests(any(), anyInt())).thenReturn(List.of(request));
        when(userRepo.findById(USER_ID)).thenReturn(Optional.of(userWithPhone("0200000000")));

        worker.processReleases();

        verify(metrics).recordP0Alert();
        verifyNoInteractions(processor);
    }

    @Test
    @DisplayName("request already at max attempts is skipped without calling the processor")
    void max_attempts_request_skipped() {
        EarlyExitRequestEntity request = pendingRequest("0247654321", "vodafone");
        for (int i = 0; i < EarlyExitReleaseProcessor.MAX_ATTEMPTS; i++) {
            request.recordAttempt(Instant.now(FIXED_CLOCK));
        }
        when(requestRepo.findDueRequests(any(), anyInt())).thenReturn(List.of(request));

        worker.processReleases();

        verifyNoInteractions(processor, userRepo);
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private EarlyExitRequestEntity pendingRequest(String momoNumber, String momoProvider) {
        EarlyExitRequestEntity r = EarlyExitRequestEntity.create(
                VAULT_ID, USER_ID, "MEDICAL",
                10_000L, 500L, 9_500L,
                Instant.parse("2026-06-24T10:00:00Z"),
                momoNumber, momoProvider,
                Instant.parse("2026-06-21T10:00:00Z"));
        setField(r, "id", REQUEST_ID);
        return r;
    }

    private User userWithPhone(String phone) {
        User u = new User();
        u.setEmail("akua@stash.test");
        u.setDisplayName("Akua Mensah");
        u.setPhone(phone);
        return u;
    }

    private static void setField(Object obj, String name, Object value) {
        try {
            var f = obj.getClass().getDeclaredField(name);
            f.setAccessible(true);
            f.set(obj, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
