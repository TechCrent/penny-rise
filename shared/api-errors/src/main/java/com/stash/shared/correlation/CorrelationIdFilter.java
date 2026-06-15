package com.stash.shared.correlation;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Servlet filter that captures or generates the correlation ID for every request.
 *
 * <p>Runs before any Spring Security filter or business logic. Order is set to
 * {@link Ordered#HIGHEST_PRECEDENCE} so the correlation ID is in MDC before
 * any other filter logs anything.
 *
 * <p>Behaviour:
 * <ul>
 *   <li>If {@code X-Correlation-Id} header is present and non-blank: use it as-is.</li>
 *   <li>If absent or blank: generate a fresh UUID v4.</li>
 *   <li>The resolved ID is written into {@link CorrelationContext} (MDC + ThreadLocal).</li>
 *   <li>The resolved ID is echoed back in the response as {@code X-Correlation-Id}.</li>
 *   <li>{@link CorrelationContext#clear()} is called in the finally block to prevent
 *       thread pool leakage when threads are reused.</li>
 * </ul>
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String correlationId = request.getHeader(CorrelationContext.HEADER);

        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }

        CorrelationContext.set(correlationId);
        response.setHeader(CorrelationContext.HEADER, correlationId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            CorrelationContext.clear();
        }
    }
}
