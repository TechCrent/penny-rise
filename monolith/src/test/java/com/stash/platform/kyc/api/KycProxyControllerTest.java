package com.stash.platform.kyc.api;

import com.stash.admin.service.AdminJwtService;
import com.stash.config.SecurityConfig;
import com.stash.platform.kyc.client.KycServiceClient;
import com.stash.platform.user.domain.User;
import com.stash.platform.user.service.JwtTokenService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = KycProxyController.class)
@Import({SecurityConfig.class, JwtTokenService.class})
@TestPropertySource(properties = {
        "stash.security.jwt.signing-key=dGVzdC1zaWduaW5nLWtleS1mb3ItdGVzdHMtb25seS0zMi1jaGFycw==",
        "stash.security.jwt.verification-keys=",
        "stash.security.jwt.access-token-expiry-minutes=15"
})
@DisplayName("KycProxyController")
class KycProxyControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired JwtTokenService jwtTokenService;
    @MockBean KycServiceClient kycServiceClient;
    @MockBean AdminJwtService adminJwtService;

    private static final UUID USER_ID = UUID.fromString("018f1234-5678-7abc-8000-000000000001");

    @Test
    @DisplayName("POST /submissions forwards to kyc-service with user header")
    void createSubmission_proxied() throws Exception {
        byte[] responseBody = "{\"id\":\"sub-1\"}".getBytes();
        when(kycServiceClient.forward(
                eq(HttpMethod.POST),
                eq("/api/v1/kyc/submissions"),
                eq(USER_ID),
                any(byte[].class),
                isNull()))
                .thenReturn(ResponseEntity.status(201)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(responseBody));

        mockMvc.perform(post("/api/v1/kyc/submissions")
                        .header("Authorization", bearerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ghana_card_number\":\"GHA-123456789-0\",\"full_name\":\"Test\"}"))
                .andExpect(status().isCreated())
                .andExpect(content().json("{\"id\":\"sub-1\"}"));

        verify(kycServiceClient).forward(
                eq(HttpMethod.POST),
                eq("/api/v1/kyc/submissions"),
                eq(USER_ID),
                any(byte[].class),
                isNull());
    }

    @Test
    @DisplayName("GET /submissions/me requires authentication")
    void getMySubmission_requiresAuth() throws Exception {
        mockMvc.perform(get("/api/v1/kyc/submissions/me"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /submissions/me forwards when authenticated")
    void getMySubmission_proxied() throws Exception {
        when(kycServiceClient.forward(
                eq(HttpMethod.GET),
                eq("/api/v1/kyc/submissions/me"),
                eq(USER_ID),
                isNull(),
                isNull()))
                .thenReturn(ResponseEntity.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"status\":\"REVIEWING\"}".getBytes()));

        mockMvc.perform(get("/api/v1/kyc/submissions/me")
                        .header("Authorization", bearerToken()))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"status\":\"REVIEWING\"}"));
    }

    private String bearerToken() {
        User user = new User("test@example.com", "hash", "Test");
        user.setId(USER_ID);
        return "Bearer " + jwtTokenService.issue(user);
    }
}
