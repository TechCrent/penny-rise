package com.stash.shared.time;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

/**
 * Injectable clock abstraction for UTC time access.
 *
 * <p>All production code that needs the current time must accept a {@code Clock}
 * as a constructor parameter rather than calling {@link Instant#now()} directly.
 * This makes time-dependent logic fully deterministic in tests by injecting a
 * {@link FixedClock} with a known instant.
 *
 * <h2>Usage in production (Spring bean)</h2>
 * <pre>
 *   {@literal @}Service
 *   public class VaultService {
 *       private final Clock clock;
 *
 *       public VaultService(Clock clock) {
 *           this.clock = clock;
 *       }
 *
 *       public void doSomething() {
 *           Instant now = clock.now();  // always UTC
 *       }
 *   }
 * </pre>
 *
 * <h2>Usage in tests</h2>
 * <pre>
 *   Clock fixed = FixedClock.of(Instant.parse("2025-01-15T10:00:00Z"));
 *   VaultService service = new VaultService(fixed);
 *   // now is deterministically 2025-01-15T10:00:00Z in every test run
 * </pre>
 *
 * <p>Never use {@code LocalDateTime} or any timezone-offset time inside
 * business logic. All storage and processing uses UTC {@link Instant}.
 * Ghana local time conversion is available in {@link TimeUtils} for
 * presentation use only.
 */
public interface Clock {

    /**
     * Returns the current instant in UTC.
     *
     * @return current UTC instant
     */
    Instant now();

    /**
     * Returns the current time as a {@link ZonedDateTime} in UTC.
     * Convenience method; equivalent to {@code now().atZone(ZoneOffset.UTC)}.
     */
    default ZonedDateTime nowUtc() {
        return now().atZone(ZoneOffset.UTC);
    }
}