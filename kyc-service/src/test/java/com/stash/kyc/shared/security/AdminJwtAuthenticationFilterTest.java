package com.stash.kyc.shared.security;

import com.stash.kyc.support.KycIntegrationTestSupport;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
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

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Replaces the old PlaceholderAdminAuthFilterTest (shared-secret header).
 * Proves the real per-admin JWT wiring end to end: an admin JWT minted the
 * same way monolith's AdminJwtService signs one is accepted; anything else
 * on the admin surface is rejected.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("AdminJwtAuthenticationFilter — real per-admin JWT required on admin routes")
class AdminJwtAuthenticationFilterTest {

    // Matches src/test/resources/application.yml's stash.security.jwt.signing-key
    private static final String SIGNING_KEY_BASE64 =
            "dGVzdC1zaWduaW5nLWtleS1mb3ItdGVzdHMtb25seS0zMi1jaGFycw==";

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

    private static String adminToken(String accountType) {
        SecretKey key = Keys.hmacShaKeyFor(Base64.getDecoder().decode(SIGNING_KEY_BASE64));
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(Duration.ofMinutes(5))))
                .claims(Map.of("token_type", "ADMIN", "account_type", accountType, "role_name", accountType))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    @Test
    @DisplayName("missing Authorization header returns 403 on admin route")
    void missing_token_returns_403() throws Exception {
        mvc.perform(get("/api/v1/kyc/admin/queue"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("a customer (non-ADMIN token_type) token returns 403")
    void non_admin_token_type_returns_403() throws Exception {
        SecretKey key = Keys.hmacShaKeyFor(Base64.getDecoder().decode(SIGNING_KEY_BASE64));
        Instant now = Instant.now();
        String customerToken = Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(Duration.ofMinutes(5))))
                .claims(Map.of("token_type", "CUSTOMER"))
                .signWith(key, Jwts.SIG.HS256)
                .compact();

        mvc.perform(get("/api/v1/kyc/admin/queue")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("a valid admin JWT passes the filter (200, not 403)")
    void valid_admin_token_passes_filter() throws Exception {
        mvc.perform(get("/api/v1/kyc/admin/queue")
                        .header("Authorization", "Bearer " + adminToken("SUPER")))
                .andExpect(status().isOk());
    }
}
