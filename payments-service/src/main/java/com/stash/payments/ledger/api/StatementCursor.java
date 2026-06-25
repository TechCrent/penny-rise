package com.stash.payments.ledger.api;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/**
 * Opaque cursor for statement pagination.
 *
 * <p>Format (before base64): {@code created_at_epoch_ms:entry_id}
 * The cursor is opaque to callers — they treat it as a string token.
 */
public record StatementCursor(Instant createdAt, UUID entryId) {

    private static final String SEPARATOR = ":";

    public String encode() {
        String raw = createdAt.toEpochMilli() + SEPARATOR + entryId;
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public static StatementCursor decode(String encoded) {
        try {
            byte[] bytes = Base64.getUrlDecoder().decode(encoded);
            String raw   = new String(bytes, StandardCharsets.UTF_8);
            int    sep   = raw.indexOf(SEPARATOR);
            if (sep < 0) throw new IllegalArgumentException("No separator in cursor");
            long epochMs = Long.parseLong(raw.substring(0, sep));
            UUID entryId = UUID.fromString(raw.substring(sep + 1));
            return new StatementCursor(Instant.ofEpochMilli(epochMs), entryId);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid cursor: " + encoded, e);
        }
    }
}
