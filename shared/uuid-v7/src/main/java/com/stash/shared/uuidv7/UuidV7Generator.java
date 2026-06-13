package com.stash.shared.uuidv7;

import java.security.SecureRandom;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Generates time-ordered UUID v7 values per RFC 9562.
 *
 * <p>Structure (128 bits):
 * <pre>
 *  0                   1                   2                   3
 *  0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                           unix_ts_ms (48 bits)                |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |    unix_ts_ms  |  ver=7 (4)  |        rand_a (12 bits)       |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * | var (2) |               rand_b (62 bits)                      |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * </pre>
 *
 * <p>Monotonicity guarantee: within the same millisecond, the 12-bit
 * rand_a field is used as a monotonic counter. This ensures that two
 * calls within the same millisecond always produce ordered IDs even
 * under high-throughput conditions. If the counter overflows within
 * a single millisecond (more than 4096 calls), the generator waits
 * for the next millisecond before issuing the next ID.
 *
 * <p>Usage:
 * <pre>
 *   UUID id = UuidV7Generator.generate();
 * </pre>
 *
 * <p>Thread-safe: yes. Uses AtomicLong for the monotonic counter state.
 */
public final class UuidV7Generator {

    private static final SecureRandom RANDOM = new SecureRandom();

    // Packs lastMs (top 48 bits) and counter (bottom 16 bits) into one long
    // for a single atomic CAS operation.
    private static final AtomicLong STATE = new AtomicLong(0L);

    private static final int MAX_COUNTER = 0xFFF; // 12-bit max = 4095

    private UuidV7Generator() {
        // Static utility class — not instantiable.
    }

    /**
     * Generates a new UUID v7 value.
     *
     * @return a time-ordered, monotonic UUID v7
     */
    public static UUID generate() {
        long ms = currentMs();
        long seq;

        while (true) {
            long state = STATE.get();
            long stateMs = state >>> 16;
            long stateSeq = state & 0xFFFFL;

            long nextMs;
            long nextSeq;

            if (ms > stateMs) {
                // New millisecond — reset counter
                nextMs = ms;
                nextSeq = 0;
            } else {
                // Same millisecond — increment counter
                nextMs = stateMs;
                nextSeq = stateSeq + 1;
                if (nextSeq > MAX_COUNTER) {
                    // Counter exhausted — spin until next ms
                    ms = waitForNextMs(stateMs);
                    continue;
                }
            }

            long nextState = (nextMs << 16) | nextSeq;
            if (STATE.compareAndSet(state, nextState)) {
                ms = nextMs;
                seq = nextSeq;
                break;
            }
            // CAS failed (concurrent update) — retry
        }

        return buildUuid(ms, seq);
    }

    /**
     * Extracts the Unix timestamp (milliseconds) embedded in a UUID v7.
     *
     * @param uuid a UUID v7
     * @return milliseconds since Unix epoch
     */
    public static long extractTimestampMs(UUID uuid) {
        return uuid.getMostSignificantBits() >>> 16;
    }

    // ── Private helpers ───────────────────────────────────────────────────

    private static UUID buildUuid(long ms, long seq) {
        // Most significant 64 bits:
        //   bits 63-16 : unix_ts_ms (48 bits)
        //   bits 15-12 : version = 0x7
        //   bits 11-0  : rand_a (12-bit monotonic counter = seq)
        long msb = (ms << 16)
                | (0x7L << 12)
                | (seq & 0xFFFL);

        // Least significant 64 bits:
        //   bits 63-62 : variant = 0b10
        //   bits 61-0  : rand_b (62 random bits)
        long lsb = (RANDOM.nextLong() & 0x3FFFFFFFFFFFFFFFL)
                | 0x8000000000000000L;

        return new UUID(msb, lsb);
    }

    private static long currentMs() {
        return System.currentTimeMillis();
    }

    private static long waitForNextMs(long lastMs) {
        long now;
        do {
            now = currentMs();
        } while (now <= lastMs);
        return now;
    }
}