package com.stash.shared.correlation;

import org.slf4j.MDC;

/**
 * Thread-local accessor for the current request's correlation ID.
 *
 * <p>The correlation ID is stored in two places simultaneously:
 * <ol>
 *   <li>Slf4j MDC under the key {@value #MDC_KEY} — so it appears on every
 *       log line within the request scope automatically.</li>
 *   <li>A {@link ThreadLocal} — so non-logging code (e.g. outbound HTTP
 *       clients, RabbitMQ producers) can retrieve it without injecting a
 *       request-scoped bean.</li>
 * </ol>
 *
 * <p>The filter {@link CorrelationIdFilter} calls {@link #set} at the start
 * of each request and {@link #clear} in the finally block. Nothing else
 * should call these methods outside of tests.
 */
public final class CorrelationContext {

    /** MDC key used in Logback pattern and JSON log output. */
    public static final String MDC_KEY = "correlation_id";

    /** HTTP header name for inbound and outbound propagation. */
    public static final String HEADER = "X-Correlation-Id";

    private static final ThreadLocal<String> HOLDER = new ThreadLocal<>();

    private CorrelationContext() {}

    /**
     * Returns the correlation ID for the current thread, or {@code null}
     * if no request is active (e.g. in a background thread or test).
     */
    public static String get() {
        return HOLDER.get();
    }

    /**
     * Sets the correlation ID for the current thread and populates MDC.
     * Called by {@link CorrelationIdFilter} at the start of each request.
     */
    public static void set(String correlationId) {
        HOLDER.set(correlationId);
        MDC.put(MDC_KEY, correlationId);
    }

    /**
     * Clears the correlation ID from the current thread and MDC.
     * Called by {@link CorrelationIdFilter} in the finally block.
     */
    public static void clear() {
        HOLDER.remove();
        MDC.remove(MDC_KEY);
    }
}
