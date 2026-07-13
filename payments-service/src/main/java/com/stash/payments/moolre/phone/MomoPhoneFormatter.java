package com.stash.payments.moolre.phone;

/**
 * Formats Ghana MoMo numbers for Moolre API calls (international, no plus).
 */
public final class MomoPhoneFormatter {

    private MomoPhoneFormatter() {}

    /**
     * {@code 0XXXXXXXXX} → {@code 233XXXXXXXXX}.
     * Already-{@code 233...} left as-is; {@code +233...} has the plus stripped.
     */
    public static String toInternational(String localOrInternational) {
        if (localOrInternational == null || localOrInternational.isBlank()) {
            throw new IllegalArgumentException("phone number is required");
        }
        String digits = localOrInternational.trim().replaceAll("\\s+", "");
        if (digits.startsWith("+233")) {
            return digits.substring(1);
        }
        if (digits.startsWith("233")) {
            return digits;
        }
        if (digits.startsWith("0")) {
            return "233" + digits.substring(1);
        }
        throw new IllegalArgumentException(
                "phone number must start with 0, 233, or +233: " + digits);
    }
}
