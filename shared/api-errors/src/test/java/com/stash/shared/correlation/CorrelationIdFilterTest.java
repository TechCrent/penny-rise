package com.stash.shared.correlation;

import com.stash.shared.apierrors.GlobalExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest
@Import({
        CorrelationIdFilter.class,
        GlobalExceptionHandler.class,
        CorrelationIdFilterTest.TestController.class
})
@DisplayName("CorrelationIdFilter")
class CorrelationIdFilterTest {

    @Autowired
    MockMvc mvc;

    @RestController
    static class TestController {

        @GetMapping("/test/correlation")
        String echo() {
            return CorrelationContext.get();
        }
    }

    @SpringBootApplication(scanBasePackages = {
            "com.stash.shared.apierrors",
            "com.stash.shared.correlation"
    })
    static class TestApp {}

    @Test
    @DisplayName("present X-Correlation-Id header is forwarded as-is")
    void present_header_forwarded() throws Exception {
        String correlationId = "my-trace-abc-123";

        mvc.perform(get("/test/correlation")
                        .header("X-Correlation-Id", correlationId))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Correlation-Id", correlationId));
    }

    @Test
    @DisplayName("absent X-Correlation-Id header generates a fresh UUID")
    void absent_header_generates_uuid() throws Exception {
        String responseHeader = mvc.perform(get("/test/correlation"))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Correlation-Id"))
                .andReturn()
                .getResponse()
                .getHeader("X-Correlation-Id");

        assertThat(responseHeader).matches(
                "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    }

    @Test
    @DisplayName("correlation ID is available in CorrelationContext during request")
    void available_in_context_during_request() throws Exception {
        String correlationId = "test-context-id";

        String body = mvc.perform(get("/test/correlation")
                        .header("X-Correlation-Id", correlationId))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString()
                .replace("\"", "");

        assertThat(body).isEqualTo(correlationId);
    }

    @Test
    @DisplayName("MDC is cleared after request completes")
    void mdc_cleared_after_request() throws Exception {
        mvc.perform(get("/test/correlation")
                        .header("X-Correlation-Id", "cleanup-test"))
                .andExpect(status().isOk());

        assertThat(CorrelationContext.get()).isNull();
    }

    @Test
    @DisplayName("two consecutive requests get independent correlation IDs")
    void independent_ids_per_request() throws Exception {
        String id1 = mvc.perform(get("/test/correlation"))
                .andReturn().getResponse().getHeader("X-Correlation-Id");

        String id2 = mvc.perform(get("/test/correlation"))
                .andReturn().getResponse().getHeader("X-Correlation-Id");

        assertThat(id1).isNotEqualTo(id2);
    }
}
