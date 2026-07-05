package com.stash.shared.apierrors;

import static org.assertj.core.api.Assertions.assertThat;

import com.stash.shared.correlation.CorrelationContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

@DisplayName("GlobalExceptionHandler")
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @AfterEach
    void clearCorrelationContext() {
        CorrelationContext.clear();
    }

    @Test
    @DisplayName(
        "ResponseStatusException preserves forbidden status and reason"
    )
    void responseStatusException_preserves_status_and_reason() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationContext.HEADER, "corr-123");

        ResponseEntity<ErrorResponse> response = handler.handleResponseStatus(
            new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "BETA_ACCESS_REQUIRED: closed beta"
            ),
            request
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error().code()).isEqualTo(
            ErrorCode.FORBIDDEN.name()
        );
        assertThat(response.getBody().error().message()).isEqualTo(
            "BETA_ACCESS_REQUIRED: closed beta"
        );
        assertThat(response.getBody().error().correlation_id()).isEqualTo(
            "corr-123"
        );
    }

    @Test
    @DisplayName("Context correlation id wins over request header")
    void responseStatusException_uses_context_correlation_id() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationContext.HEADER, "header-corr");
        CorrelationContext.set("context-corr");

        ResponseEntity<ErrorResponse> response = handler.handleResponseStatus(
            new ResponseStatusException(HttpStatus.NOT_FOUND, "missing"),
            request
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error().code()).isEqualTo(
            ErrorCode.NOT_FOUND.name()
        );
        assertThat(response.getBody().error().correlation_id()).isEqualTo(
            "context-corr"
        );
    }
}
