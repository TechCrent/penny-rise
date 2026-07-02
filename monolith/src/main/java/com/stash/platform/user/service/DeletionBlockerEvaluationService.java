package com.stash.platform.user.service;

import java.util.List;
import java.util.UUID;

/**
 * Evaluates whether a user has any active blockers that prevent account deletion.
 *
 * <p>Blockers per Overall doc §18.8: active unreceived susu rotation, locked vault
 * with funds, positive standard vault balance, positive UserBalance, active split
 * recipient role.
 *
 * <p>This interface is consumed by DeletionExecutionService to re-check blockers at
 * execution time (not just at submission time). The 30-day cool-off exists precisely
 * because financial state can change during it — money may arrive after submission.
 */
public interface DeletionBlockerEvaluationService {

    /**
     * @return an empty list if the user is clear to proceed with deletion,
     *         or a non-empty list of human-readable blocker descriptions if not
     */
    List<String> evaluateBlockers(UUID userId);
}
