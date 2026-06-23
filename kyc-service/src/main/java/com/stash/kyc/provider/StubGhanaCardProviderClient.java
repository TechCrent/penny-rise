package com.stash.kyc.provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stub KYC provider per Issue Plan §0.1 — team-controlled through v0.5.
 *
 * <p>Behaviour:
 * <ul>
 *   <li>Ghana Card numbers in the configured test-approve list auto-PASS.</li>
 *   <li>Everything else auto-FAILs by default (per this issue's acceptance
 *       criteria: "rejects all others by default").</li>
 *   <li>A submission can be force-flipped to FAIL via {@link #forceReject}
 *       even if its card number is normally on the approve list — this is
 *       the "admin route to flip to REJECTED for testing the rejection
 *       path" called for in §0.1. The actual admin HTTP endpoint for this
 *       belongs to a KYC admin issue; this method is the mechanism it calls.</li>
 * </ul>
 *
 * <p><strong>PII invariant:</strong> the Ghana Card number is never logged,
 * even though this is a stub — the logging discipline should match what
 * a real provider integration would require.
 */

@Component
public class StubGhanaCardProviderClient implements GhanaCardProviderClient {

    private static final Logger log = LoggerFactory.getLogger(StubGhanaCardProviderClient.class);

    /**
     * Test Ghana Card numbers that auto-approve. Configurable via property
     * so QA/test scripts can extend the list without a code change.
     */

    private final Set<String> testApproveNumbers;

    /** In-memory override set — submission IDs force-flipped to REJECTED for testing. */
    private final Set<UUID> forcedRejections = ConcurrentHashMap.newKeySet();

    public StubGhanaCardProviderClient(
            @Value("${stash.kyc.stub-provider.test-approve-numbers:GHA-000000001-1,GHA-000000002-2,GHA-000000003-3}")
            String testApproveNumbersCsv) {
        this.testApproveNumbers = Set.copyOf(List.of(testApproveNumbersCsv.split(",")));
        log.info("StubGhanaCardProviderClient initialised with {} test-approve numbers",
                testApproveNumbers.size());
    }

    @Override

    public ProviderDecision verify(UUID submissionId, String ghanaCardNumber, String fullNameOnCard) {

// DO NOT log ghanaCardNumber
        if (forcedRejections.contains(submissionId)) {
            log.info("Stub provider: forced rejection submissionId={}", submissionId);
            return new ProviderDecision("FAIL", "stub-ref-" + submissionId, 0.0);
        }

        boolean isTestApprove = testApproveNumbers.contains(ghanaCardNumber);

        if (isTestApprove) {
            log.info("Stub provider: auto-approved (test number) submissionId={}", submissionId);
            return new ProviderDecision("PASS", "stub-ref-" + submissionId, 0.99);
        }

        log.info("Stub provider: auto-rejected (non-test number, default reject) submissionId={}",
                submissionId);
        return new ProviderDecision("FAIL", "stub-ref-" + submissionId, 0.05);
    }

    /**
     * Admin testing hook: forces the next {@link #verify} call for this
     * submission to return FAIL, regardless of the card number used.
     *
     * <p>Called by the future admin "flip to REJECTED for testing" route.
     */

    public void forceReject(UUID submissionId) {
        forcedRejections.add(submissionId);
        log.info("Stub provider: submission flagged for forced rejection submissionId={}", submissionId);
    }
}