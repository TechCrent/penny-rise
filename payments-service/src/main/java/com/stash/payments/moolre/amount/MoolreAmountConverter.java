package com.stash.payments.moolre.amount;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Converts between ledger pesewas (long) and Moolre GHS amount strings.
 */
public final class MoolreAmountConverter {

    private MoolreAmountConverter() {}

    /** e.g. {@code 2000} → {@code "20.00"} */
    public static String pesewasToGhsString(long pesewas) {
        return BigDecimal.valueOf(pesewas, 2).toPlainString();
    }

    /** e.g. {@code "20.00"} → {@code 2000} */
    public static long ghsStringToPesewas(String amount) {
        if (amount == null || amount.isBlank()) {
            throw new IllegalArgumentException("amount is required");
        }
        BigDecimal ghs = new BigDecimal(amount.trim());
        return ghs.movePointRight(2).setScale(0, RoundingMode.UNNECESSARY).longValueExact();
    }
}
