package com.stash.admin.integration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * BLOCKING DEPENDENCY — stub implementation.
 *
 * <p>Will delegate to the KYC micro-service admin history endpoint once that
 * API is available. Until then, returns an empty list.
 */
@Component
public class IntegrationKycClient {

    private static final Logger log = LoggerFactory.getLogger(IntegrationKycClient.class);

    public List<KycSubmissionRecord> getSubmissionHistoryForUser(UUID userId) {
        log.debug("IntegrationKycClient is a stub — returning empty KYC history for user {}", userId);
        return List.of();
    }
}
