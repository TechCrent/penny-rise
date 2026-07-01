package com.stash.admin.integration;

import java.time.Instant;
import java.util.UUID;

/** Single KYC submission entry from the KYC service admin API. */
public record KycSubmissionRecord(
        UUID    submissionId,
        String  status,
        String  reviewPath,
        String  decision,
        String  decisionReason,
        Instant submittedAt,
        Instant decidedAt
) {}
