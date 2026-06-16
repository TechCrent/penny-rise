package com.stash.shared.time;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Time utility helpers for the Stash platform.
 *
 * <h2>UTC rule</h2>
 * <p>All timestamps stored in the database and processed in business logic
 * are UTC {@link Instant} values. The Schema doc mandates:
 * "Times in UTC. All timestamps are TIMESTAMPTZ stored in UTC."
 *
 * <h2>Ghana local time — PRESENTATION ONLY</h2>
 * <p>Ghana Standard Time (GST) is UTC+0 — Ghana does not observe daylight
 * saving time and its offset from UTC is always zero. As of v1.0, converting
 * to "Ghana local time" is therefore a no-op at the offset level, but the
 * explicit conversion methods are provided to:
 * <ul>
 *   <li>Make the intent clear in presentation code</li>
 *   <li>Future-proof the codebase if Ghana's timezone policy changes</li>
 *   <li>Avoid accidental use of the JVM's default system timezone</li>
 * </ul>
 *
 * <p><strong>WARNING:</strong> Never use Ghana local time in business logic,
 * database storage, event payloads, or audit records. Presentation layer only.
 */
public final class TimeUtils {

    /** Ghana Standard Time zone — UTC+0, no DST. */
    public static final ZoneId GHANA_ZONE = ZoneId.of("Africa/Accra");

    /**
     * Standard display format for timestamps shown to users in Ghana.
     * Example output: "15 Jun 2025, 12:00 PM"
     */
    public static final DateTimeFormatter GHANA_DISPLAY_FORMAT =
            DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a");

    /**
     * ISO-8601 formatter — for API responses and logs.
     * Example output: "2025-06-15T12:00:00Z"
     */
    public static final DateTimeFormatter ISO_UTC_FORMAT =
            DateTimeFormatter.ISO_INSTANT;

    private TimeUtils() {}

    /**
     * Converts a UTC {@link Instant} to Ghana local time.
     *
     * <p><strong>FOR PRESENTATION USE ONLY.</strong>
     * Never store or process the returned {@link ZonedDateTime} as a timestamp.
     *
     * @param utcInstant a UTC instant
     * @return the same moment expressed in Ghana Standard Time
     */
    public static ZonedDateTime toGhanaTime(Instant utcInstant) {
        return utcInstant.atZone(GHANA_ZONE);
    }

    /**
     * Formats a UTC instant as a Ghana-localised display string.
     *
     * <p><strong>FOR PRESENTATION USE ONLY.</strong>
     * Example: {@code "15 Jun 2025, 12:00 PM"}
     *
     * @param utcInstant a UTC instant
     * @return a human-readable string in Ghana local time
     */
    public static String formatForDisplay(Instant utcInstant) {
        return toGhanaTime(utcInstant).format(GHANA_DISPLAY_FORMAT);
    }

    /**
     * Formats a UTC instant as an ISO-8601 string.
     *
     * <p>Use this for API responses, log fields, and event payloads.
     * Example: {@code "2025-06-15T12:00:00Z"}
     *
     * @param utcInstant a UTC instant
     * @return ISO-8601 UTC string
     */
    public static String formatIso(Instant utcInstant) {
        return ISO_UTC_FORMAT.format(utcInstant);
    }

    /**
     * Returns true if {@code candidate} is after {@code reference}.
     */
    public static boolean isAfter(Instant candidate, Instant reference) {
        return candidate.isAfter(reference);
    }

    /**
     * Returns true if {@code candidate} is before {@code reference}.
     */
    public static boolean isBefore(Instant candidate, Instant reference) {
        return candidate.isBefore(reference);
    }
}