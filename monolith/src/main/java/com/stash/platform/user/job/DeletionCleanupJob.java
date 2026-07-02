package com.stash.platform.user.job;

import com.stash.platform.user.domain.DeletionRequest;
import com.stash.platform.user.repository.DeletionRequestRepository;
import com.stash.platform.user.service.DeletionExecutionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Daily job that executes due deletion requests from user_module.deletion_requests.
 *
 * <p>Design notes:
 * <ul>
 *   <li>run() claims PENDING rows due for processing via SELECT FOR UPDATE SKIP LOCKED,
 *       safe for concurrent worker instances.</li>
 *   <li>Each row is processed in processOne(), which is @Transactional around the
 *       bookkeeping (updating deletion_requests). executionService.execute() itself
 *       spans HTTP calls to Payments Service, which cannot participate in a JPA
 *       transaction per Module Boundaries doc §3.1 — so execution and bookkeeping
 *       are separate concerns deliberately.</li>
 *   <li>If the process crashes mid-execute(), the row is still PENDING and is picked
 *       up again on the next run — idempotent steps make this safe.</li>
 *   <li>STILL_BLOCKED outcomes do not increment attempts and leave the row PENDING
 *       so the next daily run re-checks. This is expected behaviour, not an error.</li>
 *   <li>After MAX_ATTEMPTS failed executions the row moves to FAILED and a P0 alert
 *       is logged for manual ops intervention.</li>
 * </ul>
 */
@Component
public class DeletionCleanupJob {

    private static final Logger log          = LoggerFactory.getLogger(DeletionCleanupJob.class);
    private static final int    BATCH_SIZE   = 100;
    private static final int    MAX_ATTEMPTS = 5;

    private final DeletionRequestRepository requestRepository;
    private final DeletionExecutionService  executionService;
    private final Clock                     clock;

    public DeletionCleanupJob(DeletionRequestRepository requestRepository,
                               DeletionExecutionService executionService,
                               Clock clock) {
        this.requestRepository = requestRepository;
        this.executionService  = executionService;
        this.clock             = clock;
    }

    @Scheduled(cron = "0 0 4 * * *", zone = "UTC")
    public void run() {
        Instant now = Instant.now(clock);
        List<DeletionRequest> due = requestRepository.claimDueRequests(now, BATCH_SIZE);
        log.info("Deletion cleanup job: {} requests due", due.size());
        for (DeletionRequest request : due) {
            processOne(request);
        }
    }

    @Transactional
    void processOne(DeletionRequest request) {
        var outcome = executionService.execute(request.getId(), request.getUserId());

        switch (outcome) {
            case SUCCEEDED -> {
                request.markCompleted(Instant.now(clock));
                requestRepository.save(request);
                log.info("Deletion completed for request {}", request.getId());
            }
            case STILL_BLOCKED -> {
                // No state change — stays PENDING, no attempts increment.
                // The daily re-run will check again tomorrow.
            }
            case FAILED_THIS_ATTEMPT -> {
                request.incrementAttempt();
                if (request.getAttempts() >= MAX_ATTEMPTS) {
                    request.markFailed();
                    log.error("[P0_ALERT] Deletion request {} FAILED after {} attempts — user {} needs manual review",
                            request.getId(), request.getAttempts(), request.getUserId());
                }
                requestRepository.save(request);
            }
        }
    }
}
