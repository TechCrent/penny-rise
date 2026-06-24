package com.stash.kyc.submission.api.dto;

/**
 * Used by both /approve and /reject (reason is required on reject and
 * ignored on approve — enforced in the service, not via two separate
 * @Valid annotation sets, since the field is the same shape either way).
 */
public record AdminDecisionRequest(String reason) {}
