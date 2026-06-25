package com.stash.payments.shared.security;

import com.stash.payments.shared.startup.LedgerGrantsVerifier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies that InternalServiceAuthFilter correctly guards /internal/** routes.
 *
 * <p>Runs against the shared dev database (stash-payments-db on localhost:15433).
 * Flyway is disabled — schema was applied in earlier migrations.
 * RabbitMQ is excluded from autoconfiguration.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = {
        "spring.flyway.enabled=false",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration",
        "PAYMENTS_DB_USER=stash_payments",
        "PAYMENTS_DB_PASSWORD=payments_local_pass",
        "PAYSTACK_SECRET_KEY=sk_test_placeholder_for_tests_only",
        "PAYSTACK_WEBHOOK_SECRET=test-webhook-secret-placeholder",
        "PAYSTACK_SETTLEMENT_ACCOUNT_ID=00000000-0000-0000-0000-000000000001",
        "stash.internal.service-token=test-internal-token-32-chars-long!!"
    }
)
@AutoConfigureMockMvc
class InternalServiceAuthFilterTest {

    @Autowired MockMvc mockMvc;

    @MockBean LedgerGrantsVerifier ledgerGrantsVerifier;
    @MockBean ConnectionFactory    connectionFactory;

    private static final String VALID_TOKEN = "test-internal-token-32-chars-long!!";

    @Test
    @DisplayName("missing token returns 401")
    void missing_token_returns_401() throws Exception {
        mockMvc.perform(post("/internal/v1/transactions/transfers")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INTERNAL_AUTH_FAILED"));
    }

    @Test
    @DisplayName("wrong token returns 401")
    void wrong_token_returns_401() throws Exception {
        mockMvc.perform(post("/internal/v1/transactions/transfers")
                        .header("X-Internal-Service-Token", "wrong-token")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("public routes are not affected by the internal filter")
    void public_route_not_filtered() throws Exception {
        mockMvc.perform(post("/api/v1/transactions/deposits")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(result ->
                        assertThat(result.getResponse().getContentAsString())
                        .doesNotContain("INTERNAL_AUTH_FAILED"));
    }
}
