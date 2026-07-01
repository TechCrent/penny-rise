package com.stash.admin.api.dto;

import java.time.Instant;
import java.util.UUID;

public record AdminKycSubmissionSummary(
        UUID    submissionId,
        String  status,
        String  reviewPath,
        String  decision,
        String  decisionReason,
        Instant submittedAt,
        Instant decidedAt
) {}
