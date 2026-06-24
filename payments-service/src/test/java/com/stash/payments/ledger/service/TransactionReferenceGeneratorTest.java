package com.stash.payments.ledger.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class TransactionReferenceGeneratorTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-06-01T00:00:00Z"), ZoneOffset.UTC);

    private final TransactionReferenceGenerator generator =
            new TransactionReferenceGenerator(FIXED_CLOCK);

    private static final Pattern REFERENCE_PATTERN =
            Pattern.compile("^STSH-\\d{6}-[A-Z0-9]{6}$");

    @Test
    @DisplayName("generated reference matches STSH-yyyymm-XXXXXX format")
    void format_matches_spec() {
        String ref = generator.generate();
        assertThat(ref).matches(REFERENCE_PATTERN);
    }

    @Test
    @DisplayName("yyyymm segment reflects the fixed clock's year-month")
    void year_month_reflects_clock() {
        String ref = generator.generate();
        assertThat(ref).startsWith("STSH-202606-");
    }

    @Test
    @DisplayName("suffix is exactly 6 alphanumeric uppercase characters")
    void suffix_length_and_alphabet() {
        String ref = generator.generate();
        String suffix = ref.substring("STSH-202606-".length());
        assertThat(suffix).hasSize(6);
        assertThat(suffix).matches("[A-Z0-9]{6}");
    }

    @Test
    @DisplayName("10,000 generated references have zero collisions")
    void ten_thousand_references_are_unique() {
        Set<String> seen = new HashSet<>(10_000);
        for (int i = 0; i < 10_000; i++) {
            boolean added = seen.add(generator.generate());
            assertThat(added)
                    .as("Collision detected at iteration %d", i)
                    .isTrue();
        }
        assertThat(seen).hasSize(10_000);
    }

    @Test
    @DisplayName("year-month changes with the clock — generator is not hardcoded")
    void year_month_tracks_clock() {
        Clock januaryClock = Clock.fixed(
                Instant.parse("2027-01-15T10:00:00Z"), ZoneOffset.UTC);
        TransactionReferenceGenerator januaryGen =
                new TransactionReferenceGenerator(januaryClock);

        String ref = januaryGen.generate();
        assertThat(ref).startsWith("STSH-202701-");
    }
}
