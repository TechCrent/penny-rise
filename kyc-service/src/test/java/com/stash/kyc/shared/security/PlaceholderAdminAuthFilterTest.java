package com.stash.kyc.shared.security;

import com.stash.kyc.support.KycIntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("PlaceholderAdminAuthFilter — non-admin caller rejected")
class PlaceholderAdminAuthFilterTest {

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16")
                    .withDatabaseName("kyc_test")
                    .withUsername("test")
                    .withPassword("test");

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        KycIntegrationTestSupport.registerPostgres(registry, postgres);
    }

    @Autowired MockMvc mvc;
    @MockBean  RabbitTemplate rabbitTemplate;

    @Test
    @DisplayName("missing X-Admin-Token header returns 403 on admin route")
    void missing_token_returns_403() throws Exception {
        mvc.perform(get("/api/v1/kyc/admin/queue"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("wrong X-Admin-Token header returns 403")
    void wrong_token_returns_403() throws Exception {
        mvc.perform(get("/api/v1/kyc/admin/queue")
                        .header("X-Admin-Token", "wrong-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("correct X-Admin-Token header passes the filter (200, not 403)")
    void correct_token_passes_filter() throws Exception {
        mvc.perform(get("/api/v1/kyc/admin/queue")
                        .header("X-Admin-Token", "local-dev-admin-token-not-for-production"))
                .andExpect(status().isOk());
    }
}
