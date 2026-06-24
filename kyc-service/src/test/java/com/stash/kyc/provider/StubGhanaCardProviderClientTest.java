package com.stash.kyc.provider;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("StubGhanaCardProviderClient")
class StubGhanaCardProviderClientTest {
    private final StubGhanaCardProviderClient client =
            new StubGhanaCardProviderClient("GHA-111111111-1,GHA-222222222-2");

    @Test
    @DisplayName("test card number auto-approves")
    void test_number_approves() {
        var decision = client.verify(UUID.randomUUID(), "GHA-111111111-1", "Test User");
        assertThat(decision.isApproved()).isTrue();
        assertThat(decision.decision()).isEqualTo("PASS");
    }

    @Test
    @DisplayName("non-test card number auto-rejects by default")
    void non_test_number_rejects() {
        var decision = client.verify(UUID.randomUUID(), "GHA-999999999-9", "Test User");
        assertThat(decision.isApproved()).isFalse();
        assertThat(decision.decision()).isEqualTo("FAIL");
    }

    @Test
    @DisplayName("forceReject overrides a normally-approved test number")
    void force_reject_overrides() {
        UUID submissionId = UUID.randomUUID();
        client.forceReject(submissionId);
        var decision = client.verify(submissionId, "GHA-111111111-1", "Test User");
        assertThat(decision.isApproved()).isFalse();
    }

    @Test
    @DisplayName("forceReject does not affect other submissions")
    void force_reject_scoped_to_submission() {
        UUID forced  = UUID.randomUUID();
        UUID normal  = UUID.randomUUID();
        client.forceReject(forced);
        var decision = client.verify(normal, "GHA-111111111-1", "Test User");
        assertThat(decision.isApproved()).isTrue();
    }
}
