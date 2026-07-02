package com.stash.platform.user.service;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * STUB — provides the Spring bean so the application context starts.
 *
 * <p>The real implementation belongs in v0.2-019 (blocker evaluation at submission
 * time). Until that issue is built, every deletion execution attempt will return
 * FAILED_THIS_ATTEMPT (this stub throws UnsupportedOperationException, which
 * DeletionExecutionService's catch block converts to FAILED_THIS_ATTEMPT).
 *
 * <p>No destructive steps will occur while this stub is in place — all deletion
 * requests stay in PENDING until a real implementation is wired.
 */
@Service
class DeletionBlockerEvaluationServiceStub implements DeletionBlockerEvaluationService {

    @Override
    public List<String> evaluateBlockers(UUID userId) {
        throw new UnsupportedOperationException(
                "DeletionBlockerEvaluationService not yet implemented — v0.2-019 scope");
    }
}
