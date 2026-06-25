package com.stash.payments.ledger.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class StatementCursorTest {

    @Test
    @DisplayName("encode → decode round trip preserves created_at and entry_id")
    void encode_decode_roundtrip() {
        Instant ts = Instant.parse("2026-06-24T10:00:00.123Z");
        UUID    id = UUID.randomUUID();

        String         encoded = new StatementCursor(ts, id).encode();
        StatementCursor decoded = StatementCursor.decode(encoded);

        assertThat(decoded.createdAt()).isEqualTo(ts);
        assertThat(decoded.entryId()).isEqualTo(id);
    }

    @Test
    @DisplayName("encoded cursor is URL-safe (no +, /, or =)")
    void encoded_cursor_is_url_safe() {
        String encoded = new StatementCursor(Instant.now(), UUID.randomUUID()).encode();
        assertThat(encoded).doesNotContain("+", "/", "=");
    }

    @Test
    @DisplayName("decoding an invalid cursor throws IllegalArgumentException")
    void invalid_cursor_throws() {
        assertThatThrownBy(() -> StatementCursor.decode("not-a-cursor"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid cursor");
    }

    @Test
    @DisplayName("different cursors produce different encoded strings")
    void different_cursors_produce_different_tokens() {
        String a = new StatementCursor(Instant.parse("2026-06-01T00:00:00Z"), UUID.randomUUID()).encode();
        String b = new StatementCursor(Instant.parse("2026-06-02T00:00:00Z"), UUID.randomUUID()).encode();
        assertThat(a).isNotEqualTo(b);
    }
}
