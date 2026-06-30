package com.stash.platform.transfer.service;

import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/**
 * Encodes and decodes the composite (created_at, id) cursor for the transfer list.
 *
 * Format (before base64): {@code <epoch_millis>:<uuid>}
 * Example: {@code 1750763521000:550e8400-e29b-41d4-a716-446655440000}
 *
 * The cursor is opaque to the client — they pass it back verbatim.
 * Decoding a tampered or malformed cursor returns null, causing the
 * query to start from the beginning (safe default).
 */
final class TransferCursor {

    private TransferCursor() {}

    static String encode(Instant createdAt, UUID id) {
        String raw = createdAt.toEpochMilli() + ":" + id;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes());
    }

    static DecodedCursor parse(String cursor) {
        if (cursor == null || cursor.isBlank()) return null;
        try {
            String   raw   = new String(Base64.getUrlDecoder().decode(cursor));
            String[] parts = raw.split(":", 2);
            Instant  time  = Instant.ofEpochMilli(Long.parseLong(parts[0]));
            UUID     id    = UUID.fromString(parts[1]);
            return new DecodedCursor(time, id);
        } catch (Exception e) {
            return null;
        }
    }

    record DecodedCursor(Instant time, UUID id) {}
}
