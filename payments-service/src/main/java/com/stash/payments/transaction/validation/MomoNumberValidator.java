package com.stash.payments.transaction.validation;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * Validates a Ghana mobile money number matches the selected provider's
 * known number-prefix ranges. This is payments-service's own copy of the
 * same check monolith's deposit/withdrawal/early-exit services perform —
 * duplicated rather than shared because the two are separate deployable
 * modules with no shared library between them. Kept as a defense-in-depth
 * check since this internal endpoint is reachable directly (via
 * X-Internal-Service-Token), not only through monolith's proxy.
 */
public final class MomoNumberValidator {

    private static final Map<String, Pattern> PATTERNS_BY_PROVIDER = Map.of(
            "mtn", Pattern.compile("^0(24|25|53|54|55|59)\\d{7}$"),
            "vodafone", Pattern.compile("^0(20|50)\\d{7}$"),
            "airteltigo", Pattern.compile("^0(26|27|56|57)\\d{7}$")
    );

    private MomoNumberValidator() {}

    /**
     * @param provider   the provider id (mtn/vodafone/airteltigo) — case-insensitive
     * @param momoNumber the MoMo number to validate
     * @throws ResponseStatusException 422 if the provider is unrecognised, either field is
     *                                 blank, or the number doesn't match the provider's prefixes
     */
    public static void validate(String provider, String momoNumber) {
        if (provider == null || provider.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "mobile_provider is required for MOMO deposits.");
        }
        if (momoNumber == null || momoNumber.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "mobile_number is required for MOMO deposits.");
        }

        Pattern pattern = PATTERNS_BY_PROVIDER.get(provider.toLowerCase());
        if (pattern == null) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "mobile_provider must be one of: mtn, vodafone, airteltigo.");
        }

        if (!pattern.matcher(momoNumber).matches()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "mobile_number does not look like a valid " + provider +
                    " number (expected 10 digits with a " + provider + " prefix).");
        }
    }
}
