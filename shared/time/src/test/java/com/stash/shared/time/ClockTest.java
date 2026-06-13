package com.stash.shared.time;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

@DisplayName("Clock and TimeUtils")
class ClockTest {

    // ── SystemClock ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("SystemClock")
    class SystemClockTest {

        @Test
        @DisplayName("returns a non-null instant")
        void returns_non_null() {
            assertThat(SystemClock.INSTANCE.now()).isNotNull();
        }

        @Test
        @DisplayName("returned instant is close to wall-clock time")
        void close_to_wall_clock() {
            long before = System.currentTimeMillis();
            Instant now = SystemClock.INSTANCE.now();
            long after = System.currentTimeMillis();

            assertThat(now.toEpochMilli())
                    .isGreaterThanOrEqualTo(before)
                    .isLessThanOrEqualTo(after);
        }

        @Test
        @DisplayName("nowUtc() returns ZonedDateTime in UTC offset")
        void now_utc_zone() {
            var zdt = SystemClock.INSTANCE.nowUtc();
            assertThat(zdt.getOffset()).isEqualTo(ZoneOffset.UTC);
        }
    }

    // ── FixedClock ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("FixedClock")
    class FixedClockTest {

        private static final String FIXED_ISO = "2025-06-15T12:00:00Z";
        private static final Instant FIXED_INSTANT = Instant.parse(FIXED_ISO);

        @Test
        @DisplayName("always returns the fixed instant")
        void always_fixed() {
            Clock clock = FixedClock.of(FIXED_INSTANT);
            assertThat(clock.now()).isEqualTo(FIXED_INSTANT);
            assertThat(clock.now()).isEqualTo(FIXED_INSTANT); // deterministic
        }

        @Test
        @DisplayName("can be created from ISO string")
        void from_string() {
            Clock clock = FixedClock.of(FIXED_ISO);
            assertThat(clock.now()).isEqualTo(FIXED_INSTANT);
        }

        @Test
        @DisplayName("rejects null instant")
        void rejects_null() {
            assertThatNullPointerException()
                    .isThrownBy(() -> FixedClock.of((Instant) null));
        }

        @Test
        @DisplayName("enables deterministic tests — two services sharing a fixed clock return same time")
        void deterministic_across_services() {
            Clock clock = FixedClock.of("2025-01-01T00:00:00Z");

            // Simulates two services both using the same injected clock
            Instant timeInServiceA = clock.now();
            Instant timeInServiceB = clock.now();

            assertThat(timeInServiceA).isEqualTo(timeInServiceB);
        }

        @Test
        @DisplayName("nowUtc() returns fixed instant in UTC zone")
        void now_utc_zone() {
            Clock clock = FixedClock.of(FIXED_INSTANT);
            var zdt = clock.nowUtc();
            assertThat(zdt.toInstant()).isEqualTo(FIXED_INSTANT);
            assertThat(zdt.getOffset()).isEqualTo(ZoneOffset.UTC);
        }
    }

    // ── TimeUtils ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("TimeUtils")
    class TimeUtilsTest {

        private static final Instant UTC_INSTANT = Instant.parse("2025-06-15T12:30:00Z");

        @Test
        @DisplayName("toGhanaTime returns ZonedDateTime in Africa/Accra zone")
        void to_ghana_time_zone() {
            var gst = TimeUtils.toGhanaTime(UTC_INSTANT);
            assertThat(gst.getZone()).isEqualTo(TimeUtils.GHANA_ZONE);
        }

        @Test
        @DisplayName("Ghana UTC+0 means same wall-clock time as UTC")
        void ghana_is_utc_plus_zero() {
            var gst = TimeUtils.toGhanaTime(UTC_INSTANT);
            // Ghana is UTC+0, so local time equals UTC time
            assertThat(gst.getHour()).isEqualTo(12);
            assertThat(gst.getMinute()).isEqualTo(30);
        }

        @Test
        @DisplayName("formatForDisplay produces human-readable Ghana time string")
        void format_for_display() {
            String display = TimeUtils.formatForDisplay(UTC_INSTANT);
            // Expected: "15 Jun 2025, 12:30 PM"
            assertThat(display).contains("15").contains("Jun").contains("2025").contains("12:30");
        }

        @Test
        @DisplayName("formatIso produces ISO-8601 UTC string with Z suffix")
        void format_iso() {
            String iso = TimeUtils.formatIso(UTC_INSTANT);
            assertThat(iso).isEqualTo("2025-06-15T12:30:00Z");
        }

        @Test
        @DisplayName("isAfter returns true when candidate is later")
        void is_after() {
            Instant later = UTC_INSTANT.plusSeconds(1);
            assertThat(TimeUtils.isAfter(later, UTC_INSTANT)).isTrue();
            assertThat(TimeUtils.isAfter(UTC_INSTANT, later)).isFalse();
        }

        @Test
        @DisplayName("isBefore returns true when candidate is earlier")
        void is_before() {
            Instant earlier = UTC_INSTANT.minusSeconds(1);
            assertThat(TimeUtils.isBefore(earlier, UTC_INSTANT)).isTrue();
            assertThat(TimeUtils.isBefore(UTC_INSTANT, earlier)).isFalse();
        }
    }
}