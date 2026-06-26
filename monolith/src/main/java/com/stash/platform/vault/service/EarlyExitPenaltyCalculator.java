package com.stash.platform.vault.service;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Calculates the early-exit penalty and release amount.
 *
 * <p><strong>Rounding:</strong> The Schema doc §3.2 specifies "rounded down
 * to the nearest pesewa" — {@link RoundingMode#FLOOR}. This means the platform
 * always receives at most 5%, never more. The issue description says "banker's
 * rounding" (HALF_EVEN) — the Schema doc takes precedence for financial calculations.
 * See tracking flag.
 *
 * <p><strong>Zero balance:</strong> if the vault has zero balance, penalty is 0
 * and release is 0. The endpoint proceeds; the early-exit request is still created
 * (the user gets 72 hours to reconsider even on a zero-balance vault).
 *
 * <p><strong>Arithmetic safety:</strong> all calculations use {@link BigDecimal}
 * to avoid floating-point precision errors. The result is rounded and converted
 * back to {@code long} (pesewas).
 */
@Component
public class EarlyExitPenaltyCalculator {

    private static final BigDecimal PENALTY_RATE = new BigDecimal("0.05");

    /**
     * Calculates penalty and release amounts for an early exit.
     *
     * @param balancePesewas the vault balance at the time of the request
     * @return immutable result with penalty and release amounts
     */
    public PenaltyResult calculate(long balancePesewas) {
        if (balancePesewas <= 0) {
            return new PenaltyResult(0L, 0L, balancePesewas);
        }
        BigDecimal balance = BigDecimal.valueOf(balancePesewas);
        // FLOOR: platform never receives more than 5% due to rounding
        long penalty = balance.multiply(PENALTY_RATE)
                .setScale(0, RoundingMode.FLOOR)
                .longValue();
        long release = balancePesewas - penalty;
        return new PenaltyResult(penalty, release, balancePesewas);
    }

    public record PenaltyResult(long penaltyAmount, long releaseAmount, long balanceAtRequest) {
        /**
         * Sanity check — should always hold; catches calculation bugs.
         */
        public boolean isValid() {
            return penaltyAmount >= 0
                    && releaseAmount >= 0
                    && penaltyAmount + releaseAmount == balanceAtRequest;
        }
    }
}
