package com.stash.shared.time;

import java.time.Instant;
import java.util.Objects;

/**
 * Test {@link Clock} implementation that always returns a fixed instant.
 *
 * <p>Use this in unit tests to make time-dependent logic fully deterministic:
 * <pre>
 *   // In your test:
 *   Clock clock = FixedClock.of(Instant.parse("2025-06-15T12:00:00Z"));
 *   MyService service = new MyService(clock);
 *
 *   // clock.now() will always return 2025-06-15T12:00:00Z
 *   assertThat(service.createRecord().getCreatedAt())
 *       .isEqualTo(Instant.parse("2025-06-15T12:00:00Z"));
 * </pre>
 *
 * <p>This class is in the main source set (not test) so it can be used
 * by any module that depends on {@code shared/time} in its test scope.
 */
public final class FixedClock implements Clock {

    private final Instant fixedInstant;

    private FixedClock(Instant fixedInstant) {
        this.fixedInstant = Objects.requireNonNull(fixedInstant, "fixedInstant must not be null");
    }

    /**
     * Creates a fixed clock at the given instant.
     *
     * @param instant the fixed UTC instant to return on every {@link #now()} call
     * @return a fixed clock
     */
    public static FixedClock of(Instant instant) {
        return new FixedClock(instant);
    }

    /**
     * Creates a fixed clock parsed from an ISO-8601 string.
     *
     * <p>Example: {@code FixedClock.of("2025-06-15T12:00:00Z")}
     *
     * @param isoInstant ISO-8601 UTC instant string (must end with 'Z')
     * @return a fixed clock
     */
    public static FixedClock of(String isoInstant) {
        return new FixedClock(Instant.parse(isoInstant));
    }

    @Override
    public Instant now() {
        return fixedInstant;
    }

    @Override
    public String toString() {
        return "FixedClock[" + fixedInstant + "]";
    }
}