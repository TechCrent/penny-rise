package com.stash.kyc.provider;

import java.util.UUID;

/**
 * Abstraction over the third-party Ghana Card verification provider.
 *
 * <p>Per Issue Plan §0.1: "Third-party KYC provider: Stub implementation
 * under team control through v0.5 (auto-approves submissions matching
 * test Ghana Card numbers; admin route to flip to REJECTED for testing
 * the rejection path). Real provider integration is part of Public
 * Launch Readiness."
 *
 * <p>This interface is the swap point. {@link StubGhanaCardProviderClient}
 * is the only implementation through v0.5. A real implementation (Smile
 * Identity, Youverify, Dojah — see Must Research doc) plugs in here without
 * touching {@link com.stash.kyc.submission.service.AutomatedDecisionService}.
 */

public interface GhanaCardProviderClient {

    /**
     * Requests a verification decision for a submission.
     *
     * @param submissionId      the submission being verified
     * @param ghanaCardNumber   the decrypted Ghana Card number
     * @param fullNameOnCard    the name as entered
     * @return the provider's decision
     */

    ProviderDecision verify(UUID submissionId, String ghanaCardNumber, String fullNameOnCard);
    /**
     * Result of a provider verification call.
     */

    record ProviderDecision(
            String decision,           // PASS, FLAGGED, FAIL, ERROR, TIMEOUT
            String providerReference,  // provider's tracking ID for this check
            Double confidenceScore     // 0.0-1.0, nullable
    ) {
        public boolean isApproved() {
            return "PASS".equals(decision);
        }
    }
}
