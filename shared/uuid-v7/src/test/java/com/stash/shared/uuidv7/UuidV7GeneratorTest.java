package com.stash.shared.uuidv7;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("UuidV7Generator")
class UuidV7GeneratorTest {

    @Test
    @DisplayName("generates a non-null UUID")
    void generates_non_null() {
        assertThat(UuidV7Generator.generate()).isNotNull();
    }

    @Test
    @DisplayName("version field is 7")
    void version_is_7() {
        UUID id = UuidV7Generator.generate();
        assertThat(id.version()).isEqualTo(7);
    }

    @Test
    @DisplayName("variant field is IETF RFC 4122 (2)")
    void variant_is_rfc4122() {
        UUID id = UuidV7Generator.generate();
        assertThat(id.variant()).isEqualTo(2);
    }

    @Test
    @DisplayName("timestamp extracted from UUID matches generation time within 5 seconds")
    void timestamp_encoding_is_correct() {
        long before = System.currentTimeMillis();
        UUID id = UuidV7Generator.generate();
        long after = System.currentTimeMillis();

        long embedded = UuidV7Generator.extractTimestampMs(id);

        assertThat(embedded)
                .isGreaterThanOrEqualTo(before)
                .isLessThanOrEqualTo(after + 5000);
    }

    @Test
    @DisplayName("IDs sort lexicographically by creation time")
    void ids_sort_by_creation_time() throws InterruptedException {
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            ids.add(UuidV7Generator.generate());
            // Small sleep every 10 to ensure timestamp advances across iterations
            if (i % 10 == 0) Thread.sleep(1);
        }

        for (int i = 1; i < ids.size(); i++) {
            UUID prev = ids.get(i - 1);
            UUID curr = ids.get(i);
            assertThat(curr.toString().compareTo(prev.toString()))
                    .as("ID at index %d should be lexicographically greater than index %d", i, i - 1)
                    .isGreaterThan(0);
        }
    }

    @Test
    @DisplayName("monotonic within the same millisecond — rapid burst stays ordered")
    void monotonic_within_same_millisecond() {
        // Generate 4096 IDs as fast as possible (likely same ms)
        int count = 4096;
        List<UUID> ids = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            ids.add(UuidV7Generator.generate());
        }

        for (int i = 1; i < ids.size(); i++) {
            UUID prev = ids.get(i - 1);
            UUID curr = ids.get(i);
            assertThat(curr.toString().compareTo(prev.toString()))
                    .as("Burst ID %d must be > ID %d", i, i - 1)
                    .isGreaterThan(0);
        }
    }

    @Test
    @DisplayName("collision resistance — 1M generations produce no duplicates")
    void no_collisions_over_1M_generations() {
        int count = 1_000_000;
        Set<UUID> seen = new HashSet<>(count);
        for (int i = 0; i < count; i++) {
            UUID id = UuidV7Generator.generate();
            assertThat(seen.add(id))
                    .as("Duplicate UUID detected at iteration %d: %s", i, id)
                    .isTrue();
        }
    }
}