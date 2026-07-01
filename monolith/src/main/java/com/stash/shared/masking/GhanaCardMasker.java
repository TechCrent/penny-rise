package com.stash.shared.masking;

public final class GhanaCardMasker {

    private static final String MASK_PREFIX = "••••••••";

    private GhanaCardMasker() {}

    /**
     * Returns a masked representation of the Ghana Card number that reveals
     * only the last four alphanumeric characters. The fixed-length prefix
     * ensures the raw digit count is never inferred from the masked output.
     *
     * @param ghanaCardNumber the raw Ghana Card number, or {@code null}
     * @return masked string, {@code null} if input is {@code null}
     */
    public static String maskToLastFour(String ghanaCardNumber) {
        if (ghanaCardNumber == null) return null;
        if (ghanaCardNumber.isBlank()) return ghanaCardNumber;

        String alnum = ghanaCardNumber.chars()
                .filter(Character::isLetterOrDigit)
                .collect(StringBuilder::new,
                         StringBuilder::appendCodePoint,
                         StringBuilder::append)
                .toString();

        if (alnum.length() <= 4) {
            return MASK_PREFIX;
        }

        return MASK_PREFIX + alnum.substring(alnum.length() - 4);
    }
}
