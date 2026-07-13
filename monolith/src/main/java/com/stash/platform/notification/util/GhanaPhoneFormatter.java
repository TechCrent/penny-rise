package com.stash.platform.notification.util;

import java.util.Optional;

/**
 * Formats Ghanaian phone numbers for outbound providers.
 *
 * <p>Users store phones in E.164 ({@code +233501234567}). Moolre expects
 * digits without a leading plus ({@code 233501234567}).
 */
public final class GhanaPhoneFormatter {

    private GhanaPhoneFormatter() {}

    /**
     * Converts a stored Ghana phone to Moolre recipient format ({@code 233...}).
     *
     * <p>Accepts:
     * <ul>
     *   <li>{@code +233XXXXXXXXX}</li>
     *   <li>{@code 233XXXXXXXXX}</li>
     *   <li>{@code 0XXXXXXXXX} (local)</li>
     * </ul>
     *
     * @return formatted recipient, or empty if the input cannot be normalised
     */
    public static Optional<String> toMoolreRecipient(String phone) {
        if (phone == null || phone.isBlank()) {
            return Optional.empty();
        }

        String digits = phone.strip().replaceAll("[\\s-]", "");

        if (digits.startsWith("+")) {
            digits = digits.substring(1);
        }

        if (digits.startsWith("0") && digits.length() == 10) {
            digits = "233" + digits.substring(1);
        }

        if (digits.matches("^233[0-9]{9}$")) {
            return Optional.of(digits);
        }

        return Optional.empty();
    }
}
