package com.stash.shared.validation;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * Validates a Ghana mobile money number matches the selected provider's
 * known number-prefix ranges, so a user can't pick "MTN" and submit an
 * arbitrary string as the number.
 *
 * <p>Shared by deposit, withdrawal, and early-exit flows — each separately
 * collects a MoMo destination/source number and previously only checked
 * that the fields were non-blank (deposit) or that the provider was one of
 * the three known values (withdrawal/early-exit), with no check tying the
 * two together.
 */
public final class MomoNumberValidator {

    private static final Map<String, Pattern> PATTERNS_BY_PROVIDER = Map.of(
            "mtn", Pattern.compile("^0(24|25|53|54|55|59)\\d{7}$"),
            "vodafone", Pattern.compile("^0(20|50)\\d{7}$"),
            "airteltigo", Pattern.compile("^0(26|27|56|57)\\d{7}$")
    );

    private MomoNumberValidator() {}

    /**
     * @param provider      the provider id (mtn/vodafone/airteltigo) — case-insensitive
     * @param momoNumber    the MoMo number to validate
     * @param providerField JSON field name to reference in error messages (e.g. "mobile_provider")
     * @param numberField   JSON field name to reference in error messages (e.g. "mobile_number")
     * @throws ResponseStatusException 422 if the provider is unrecognised, either field is
     *                                 blank, or the number doesn't match the provider's prefixes
     */
    public static void validate(String provider, String momoNumber,
                                 String providerField, String numberField) {
        if (provider == null || provider.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    providerField + " is required.");
        }
        if (momoNumber == null || momoNumber.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    numberField + " is required.");
        }

        Pattern pattern = PATTERNS_BY_PROVIDER.get(provider.toLowerCase());
        if (pattern == null) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    providerField + " must be one of: mtn, vodafone, airteltigo.");
        }

        if (!pattern.matcher(momoNumber).matches()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    numberField + " does not look like a valid " + provider +
                    " number (expected 10 digits with a " + provider + " prefix).");
        }
    }
}
