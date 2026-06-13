package com.stash.shared.money;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * Presentation-layer formatting for {@link Money} values.
 *
 * <p><strong>WARNING — FOR UI USE ONLY.</strong>
 * This class converts pesewas to cedis for display. The resulting
 * {@code String} or {@code double} values must NEVER be used in business
 * logic, stored in the database, or passed to arithmetic operations.
 * The only correct storage representation is pesewas as {@code BIGINT}.
 *
 * <p>Correct usage:
 * <pre>
 *   // Display only:
 *   String display = MoneyFormatter.format(vault.getBalance()); // "₵12.50"
 *
 *   // Wrong — never do this:
 *   double cedis = MoneyFormatter.toCedis(amount); // then do arithmetic on it
 * </pre>
 */
public final class MoneyFormatter {

    private MoneyFormatter() {}

    /**
     * Formats a {@link Money} value as a cedi string with two decimal places.
     *
     * <p>Example: 1250 pesewas → "₵12.50"
     *
     * @param money the monetary value to format
     * @return a display string, e.g. "₵12.50"
     */
    public static String format(Money money) {
        if (money == null) return money.getCurrency().symbol + "0.00";
        double cedis = toCedis(money);
        DecimalFormat df = new DecimalFormat(
                "0.00",
                DecimalFormatSymbols.getInstance(Locale.US)
        );
        return money.getCurrency().symbol + df.format(cedis);
    }

    /**
     * Formats without the currency symbol.
     *
     * <p>Example: 1250 pesewas → "12.50"
     */
    public static String formatAmount(Money money) {
        if (money == null) return "0.00";
        double cedis = toCedis(money);
        DecimalFormat df = new DecimalFormat(
                "0.00",
                DecimalFormatSymbols.getInstance(Locale.US)
        );
        return df.format(cedis);
    }

    /**
     * Converts pesewas to cedis as a double.
     *
     * <p><strong>FOR DISPLAY ONLY.</strong> Never use the result in arithmetic.
     *
     * @param money the monetary value
     * @return cedi value as a double (display-only)
     */
    public static double toCedis(Money money) {
        if (money == null) return 0.0;
        return (double) money.getPesewas() / money.getCurrency().subunitsPerUnit;
    }
}