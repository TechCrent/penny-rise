package com.stash.shared.time;

import java.time.Instant;

/**
 * Production {@link Clock} implementation that delegates to the system clock.
 *
 * <p>Register this as a Spring {@code @Bean} in each service's configuration:
 * <pre>
 *   {@literal @}Configuration
 *   public class TimeConfig {
 *       {@literal @}Bean
 *       public Clock clock() {
 *           return SystemClock.INSTANCE;
 *       }
 *   }
 * </pre>
 *
 * <p>Always returns UTC instants. The JVM timezone setting is irrelevant —
 * {@link Instant#now()} is always UTC by definition.
 */
public final class SystemClock implements Clock {

    /** Singleton — stateless, safe to share. */
    public static final SystemClock INSTANCE = new SystemClock();

    private SystemClock() {}

    @Override
    public Instant now() {
        return Instant.now();
    }

    @Override
    public String toString() {
        return "SystemClock[UTC]";
    }
}