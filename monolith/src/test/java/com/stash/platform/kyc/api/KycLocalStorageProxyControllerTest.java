package com.stash.platform.kyc.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.stash.admin.rbac.AdminAccessDeniedHandler;
import com.stash.admin.service.AdminJwtService;
import com.stash.config.SecurityConfig;
import com.stash.platform.kyc.client.KycServiceClient;
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

@WebMvcTest(controllers = KycLocalStorageProxyController.class)
@Import({ SecurityConfig.class, JwtTokenService.class })
@TestPropertySource(
    properties = {
        "stash.security.jwt.signing-key=dGVzdC1zaWduaW5nLWtleS1mb3ItdGVzdHMtb25seS0zMi1jaGFycw==",
        "stash.security.jwt.verification-keys=",
        "stash.security.jwt.access-token-expiry-minutes=15",
    }
)
@DisplayName("KycLocalStorageProxyController")
class KycLocalStorageProxyControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    KycServiceClient kycServiceClient;

    @MockBean
    AdminJwtService adminJwtService;

    @MockBean
    AdminAccessDeniedHandler adminAccessDeniedHandler;

    @Test
    @DisplayName(
        "PUT /internal/local-storage/upload proxies binary upload without auth"
    )
    void upload_proxied() throws Exception {
        when(
            kycServiceClient.forwardPublic(
                eq(HttpMethod.PUT),
                eq(
                    "/internal/local-storage/upload/submissions/123/front.jpg?expires=123"
                ),
                any(byte[].class),
                eq(MediaType.IMAGE_JPEG),
                org.mockito.ArgumentMatchers.isNull()
            )
        ).thenReturn(ResponseEntity.status(201).build());

        mockMvc
            .perform(
                put(
                    "/internal/local-storage/upload/submissions/123/front.jpg?expires=123"
                )
                    .contentType(MediaType.IMAGE_JPEG)
                    .content("image-bytes".getBytes())
            )
            .andExpect(status().isCreated());

        verify(kycServiceClient).forwardPublic(
            eq(HttpMethod.PUT),
            eq(
                "/internal/local-storage/upload/submissions/123/front.jpg?expires=123"
            ),
            argThat(body -> {
                assertThat(body).isEqualTo("image-bytes".getBytes());
                return true;
            }),
            eq(MediaType.IMAGE_JPEG),
            org.mockito.ArgumentMatchers.isNull()
        );
    }

    @Test
    @DisplayName(
        "GET /internal/local-storage/view proxies document download without auth"
    )
    void view_proxied() throws Exception {
        byte[] imageBytes = "jpeg-bytes".getBytes();
        when(
            kycServiceClient.forwardPublic(
                eq(HttpMethod.GET),
                eq(
                    "/internal/local-storage/view/submissions/123/front.jpg?expires=123"
                ),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull()
            )
        ).thenReturn(
            ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG).body(imageBytes)
        );

        mockMvc
            .perform(
                get(
                    "/internal/local-storage/view/submissions/123/front.jpg?expires=123"
                )
            )
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.IMAGE_JPEG))
            .andExpect(content().bytes(imageBytes));

        verify(kycServiceClient).forwardPublic(
            eq(HttpMethod.GET),
            eq(
                "/internal/local-storage/view/submissions/123/front.jpg?expires=123"
            ),
            org.mockito.ArgumentMatchers.isNull(),
            org.mockito.ArgumentMatchers.isNull(),
            org.mockito.ArgumentMatchers.isNull()
        );
    }
}
