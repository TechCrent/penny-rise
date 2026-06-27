package com.stash.platform.vault.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class EarlyExitPenaltyCalculatorTest {

    private final EarlyExitPenaltyCalculator calc = new EarlyExitPenaltyCalculator();

    @Test
    @DisplayName("GHS 100.00 (10000p): penalty = GHS 5.00 (500p), release = GHS 95.00 (9500p)")
    void ghx_100() {
        var result = calc.calculate(10_000L);
        assertThat(result.penaltyAmount()).isEqualTo(500L);
        assertThat(result.releaseAmount()).isEqualTo(9_500L);
        assertThat(result.isValid()).isTrue();
    }

    @Test
    @DisplayName("1 pesewa: penalty = 0 (FLOOR of 0.05), release = 1")
    void one_pesewa() {
        var result = calc.calculate(1L);
        assertThat(result.penaltyAmount()).isEqualTo(0L);
        assertThat(result.releaseAmount()).isEqualTo(1L);
        assertThat(result.isValid()).isTrue();
    }

    @Test
    @DisplayName("19 pesewas: penalty = 0 (FLOOR of 0.95), release = 19")
    void nineteen_pesewas_floor() {
        // 5% of 19 = 0.95 pesewas → FLOOR → 0; release = 19
        var result = calc.calculate(19L);
        assertThat(result.penaltyAmount()).isEqualTo(0L);
        assertThat(result.releaseAmount()).isEqualTo(19L);
    }

    @Test
    @DisplayName("20 pesewas: penalty = 1 (FLOOR of 1.0), release = 19")
    void twenty_pesewas() {
        // 5% of 20 = 1.0 pesewas → FLOOR → 1; release = 19
        var result = calc.calculate(20L);
        assertThat(result.penaltyAmount()).isEqualTo(1L);
        assertThat(result.releaseAmount()).isEqualTo(19L);
    }

    @Test
    @DisplayName("GHS 1000.00 (100000p): penalty = GHS 50.00 (5000p), release = GHS 950.00 (95000p)")
    void ghs_1000() {
        var result = calc.calculate(100_000L);
        assertThat(result.penaltyAmount()).isEqualTo(5_000L);
        assertThat(result.releaseAmount()).isEqualTo(95_000L);
        assertThat(result.isValid()).isTrue();
    }

    @Test
    @DisplayName("large balance: 10M GHS (1_000_000_000p) penalty correct")
    void large_balance_no_overflow() {
        long balance = 1_000_000_000L;   // 10M GHS
        var result = calc.calculate(balance);
        assertThat(result.penaltyAmount()).isEqualTo(50_000_000L);
        assertThat(result.releaseAmount()).isEqualTo(950_000_000L);
        assertThat(result.isValid()).isTrue();
    }

    @Test
    @DisplayName("zero balance: penalty = 0, release = 0")
    void zero_balance() {
        var result = calc.calculate(0L);
        assertThat(result.penaltyAmount()).isEqualTo(0L);
        assertThat(result.releaseAmount()).isEqualTo(0L);
        assertThat(result.isValid()).isTrue();
    }

    @Test
    @DisplayName("all results satisfy penalty + release == balance")
    void invariant_holds_for_range() {
        for (long balance = 0; balance <= 10_000; balance++) {
            var result = calc.calculate(balance);
            assertThat(result.isValid())
                    .as("invariant failed for balance=%d", balance)
                    .isTrue();
        }
    }
}
