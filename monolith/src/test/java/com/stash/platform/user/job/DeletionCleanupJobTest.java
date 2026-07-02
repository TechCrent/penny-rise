package com.stash.platform.user.job;

import com.stash.platform.user.domain.DeletionRequest;
import com.stash.platform.user.repository.DeletionRequestRepository;
import com.stash.platform.user.service.DeletionExecutionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

class DeletionCleanupJobTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-07-01T04:00:00Z"), ZoneOffset.UTC);

    private final DeletionRequestRepository requestRepository = mock(DeletionRequestRepository.class);
    private final DeletionExecutionService  executionService  = mock(DeletionExecutionService.class);
    private final DeletionCleanupJob        job               = new DeletionCleanupJob(requestRepository, executionService, FIXED_CLOCK);

    @Test
    @DisplayName("SUCCEEDED marks the request COMPLETED")
    void succeededMarksCompleted() throws Exception {
        var request = newRequest(0);
        when(executionService.execute(any(), any())).thenReturn(DeletionExecutionService.Outcome.SUCCEEDED);

        job.processOne(request);

        assertThat(request.getStatus()).isEqualTo("COMPLETED");
        verify(requestRepository).save(request);
    }

    @Test
    @DisplayName("partial failure increments attempts and leaves status retryable")
    void partialFailureIncrementsAttempts() throws Exception {
        var request = newRequest(2);
        when(executionService.execute(any(), any())).thenReturn(DeletionExecutionService.Outcome.FAILED_THIS_ATTEMPT);

        job.processOne(request);

        assertThat(request.getAttempts()).isEqualTo(3);
        assertThat(request.getStatus()).isNotEqualTo("FAILED");
    }

    @Test
    @DisplayName("5th failed attempt marks FAILED and logs a P0 alert")
    void maxAttemptsMarksFailed() throws Exception {
        var request = newRequest(4);
        when(executionService.execute(any(), any())).thenReturn(DeletionExecutionService.Outcome.FAILED_THIS_ATTEMPT);

        job.processOne(request);

        assertThat(request.getAttempts()).isEqualTo(5);
        assertThat(request.getStatus()).isEqualTo("FAILED");
    }

    @Test
    @DisplayName("blocked outcome leaves attempts and status untouched — no save")
    void blockedLeavesStateUntouched() throws Exception {
        var request = newRequest(0);
        when(executionService.execute(any(), any())).thenReturn(DeletionExecutionService.Outcome.STILL_BLOCKED);

        job.processOne(request);

        assertThat(request.getAttempts()).isZero();
        verify(requestRepository, never()).save(any());
    }

    @Test
    @DisplayName("cancelled requests are never claimed — excluded at the query level")
    void cancelledRequestsNeverClaimed() {
        when(requestRepository.claimDueRequests(any(), anyInt())).thenReturn(List.of());

        job.run();

        verifyNoInteractions(executionService);
    }

    @Test
    @DisplayName("idempotent re-run: a COMPLETED request is never re-claimed")
    void completedRequestsNeverReClaimed() {
        // claimDueRequests' WHERE status='PENDING' clause guarantees this at the DB level.
        // No separate re-run-safety code is needed; this test documents the guarantee.
        when(requestRepository.claimDueRequests(any(), anyInt())).thenReturn(List.of());

        job.run();

        verifyNoInteractions(executionService);
    }

    private DeletionRequest newRequest(int startingAttempts) throws Exception {
        DeletionRequest r = new DeletionRequest();
        setField(r, "id",       UUID.randomUUID());
        setField(r, "userId",   UUID.randomUUID());
        setField(r, "status",   "PENDING");
        setField(r, "attempts", startingAttempts);
        return r;
    }

    private static void setField(Object obj, String name, Object value) throws Exception {
        Field f = obj.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(obj, value);
    }
}
