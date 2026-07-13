package com.stash.payments.moolre.channel;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Maps PennyRise MoMo provider ids to Moolre channel codes.
 *
 * <p>Payment collection and transfer/validate use different MTN codes
 * (13 vs 1); Vodafone/Telecel and AirtelTigo share 6 and 7 on both flows.
 */
public final class MoolreChannelMapper {

    private MoolreChannelMapper() {}

    /**
     * Channel for {@code /open/transact/payment}: 13=MTN, 6=Telecel, 7=AT.
     *
     * @throws ResponseStatusException 422 for an unrecognised provider
     */
    public static String forPayment(String provider) {
        if (provider == null || provider.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "mobile_provider is required.");
        }
        return switch (provider.toLowerCase()) {
            case "mtn" -> "13";
            case "vodafone" -> "6";
            case "airteltigo" -> "7";
            default -> throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "mobile_provider must be one of: mtn, vodafone, airteltigo.");
        };
    }

    /**
     * Channel for transfer/validate: 1=MTN, 6=Telecel, 7=AT.
     *
     * @throws ResponseStatusException 422 for an unrecognised provider
     */
    public static String forTransfer(String provider) {
        if (provider == null || provider.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "mobile_provider is required.");
        }
        return switch (provider.toLowerCase()) {
            case "mtn" -> "1";
            case "vodafone" -> "6";
            case "airteltigo" -> "7";
            default -> throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "mobile_provider must be one of: mtn, vodafone, airteltigo.");
        };
    }
}
