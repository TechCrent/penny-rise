package com.stash.payments.shared.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies that {@link InternalServiceAuthFilter} correctly guards
 * {@code /internal/**} routes.
 *
 * <p>This is a standalone web-layer unit test — it wires only the filter and a
 * stub controller, with no Spring application context, database, or message
 * broker. That keeps it fast and runnable under surefire in CI (DB-backed
 * tests live in {@code *IT} classes instead).
 */
class InternalServiceAuthFilterTest {

    private static final String VALID_TOKEN = "test-internal-token-32-chars-long!!";

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new StubController())
                .addFilters(new InternalServiceAuthFilter(VALID_TOKEN))
                .build();
    }

    @RestController
    static class StubController {

        @PostMapping("/internal/v1/transactions/transfers")
        @ResponseStatus(HttpStatus.CREATED)
        String transfer() {
            return "{\"ok\":true}";
        }

        @PostMapping("/api/v1/transactions/deposits")
        @ResponseStatus(HttpStatus.OK)
        String deposit() {
            return "{\"ok\":true}";
        }
    }

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
    @DisplayName("valid token passes the internal filter")
    void valid_token_passes_filter() throws Exception {
        mockMvc.perform(post("/internal/v1/transactions/transfers")
                        .header("X-Internal-Service-Token", VALID_TOKEN)
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("public routes are not affected by the internal filter")
    void public_route_not_filtered() throws Exception {
        mockMvc.perform(post("/api/v1/transactions/deposits")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(result ->
                        assertThat(result.getResponse().getContentAsString())
                        .doesNotContain("INTERNAL_AUTH_FAILED"));
    }
}
