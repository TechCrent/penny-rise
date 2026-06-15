package com.stash.shared.apierrors;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest
@Import({GlobalExceptionHandler.class, GlobalExceptionHandlerTest.TestController.class})
@DisplayName("GlobalExceptionHandler")
class GlobalExceptionHandlerTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper objectMapper;

    // ── Test controller ────────────────────────────────────────────────────

    @RestController
    @RequestMapping("/test")
    static class TestController {

        record Input(@NotBlank(message = "must not be blank") String name) {}

        @GetMapping("/not-found")
        void notFound() {
            throw new StashApiException(
                    ErrorCode.VAULT_NOT_FOUND,
                    "Vault not found: abc-123",
                    HttpStatus.NOT_FOUND,
                    Map.of("vault_id", "abc-123"));
        }

        @GetMapping("/conflict")
        void conflict() {
            throw new StashApiException(
                    ErrorCode.SUSU_ALREADY_MEMBER,
                    "User is already a member of this susu group.",
                    HttpStatus.CONFLICT);
        }

        @GetMapping("/boom")
        void boom() {
            throw new RuntimeException("Something went very wrong internally");
        }

        @PostMapping("/validate")
        void validate(@Valid @RequestBody Input input) {}
    }

    // Minimal Spring Boot app for slice test
    @SpringBootApplication(scanBasePackages = "com.stash.shared.apierrors")
    static class TestApp {}

    // ── Tests ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("domain exception → correct HTTP status, code, message in envelope")
    void domain_exception_404() throws Exception {
        mvc.perform(get("/test/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("VAULT_NOT_FOUND"))
                .andExpect(jsonPath("$.error.message").value("Vault not found: abc-123"))
                .andExpect(jsonPath("$.error.details.vault_id").value("abc-123"))
                .andExpect(jsonPath("$.error.correlation_id").isNotEmpty());
    }

    @Test
    @DisplayName("conflict domain exception → 409")
    void domain_exception_409() throws Exception {
        mvc.perform(get("/test/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("SUSU_ALREADY_MEMBER"));
    }

    @Test
    @DisplayName("unexpected exception → 500 INTERNAL_ERROR — no stack trace in body")
    void unexpected_exception_500() throws Exception {
        mvc.perform(get("/test/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.error.correlation_id").isNotEmpty())
                // Stack trace must NOT appear in response
                .andExpect(jsonPath("$.error.message").value(
                        org.hamcrest.Matchers.not(
                                org.hamcrest.Matchers.containsString("RuntimeException"))))
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andExpect(jsonPath("$.stackTrace").doesNotExist());
    }

    @Test
    @DisplayName("@Valid failure → 400 VALIDATION_ERROR with field errors in details")
    void validation_failure_400() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("name", ""));

        mvc.perform(post("/test/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details.name").value("must not be blank"));
    }

    @Test
    @DisplayName("correlation_id from X-Correlation-Id header is echoed in response")
    void correlation_id_echoed() throws Exception {
        String correlationId = "test-correlation-123";

        mvc.perform(get("/test/not-found")
                        .header("X-Correlation-Id", correlationId))
                .andExpect(jsonPath("$.error.correlation_id").value(correlationId));
    }

    @Test
    @DisplayName("missing X-Correlation-Id header → UUID generated")
    void correlation_id_generated_when_absent() throws Exception {
        mvc.perform(get("/test/boom"))
                .andExpect(jsonPath("$.error.correlation_id").isNotEmpty());
    }

    @Test
    @DisplayName("unmapped route → 404 NOT_FOUND envelope")
    void unmapped_route_returns_not_found_envelope() throws Exception {
        mvc.perform(get("/api/v1/nonexistent").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.error.message").value("The requested resource does not exist."))
                .andExpect(jsonPath("$.error.correlation_id").isNotEmpty());
    }
}