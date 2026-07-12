package com.stash.shared.validation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY;

class MomoNumberValidatorTest {

    @Test
    @DisplayName("valid MTN number passes for every documented MTN prefix")
    void valid_mtn_numbers_pass() {
        for (String prefix : new String[]{"024", "025", "053", "054", "055", "059"}) {
            assertThatCode(() ->
                    MomoNumberValidator.validate("mtn", prefix + "1234567", "mobile_provider", "mobile_number"))
                    .doesNotThrowAnyException();
        }
    }

    @Test
    @DisplayName("valid Vodafone number passes for every documented Vodafone/Telecel prefix")
    void valid_vodafone_numbers_pass() {
        for (String prefix : new String[]{"020", "050"}) {
            assertThatCode(() ->
                    MomoNumberValidator.validate("vodafone", prefix + "1234567", "mobile_provider", "mobile_number"))
                    .doesNotThrowAnyException();
        }
    }

    @Test
    @DisplayName("valid AirtelTigo number passes for every documented AirtelTigo prefix")
    void valid_airteltigo_numbers_pass() {
        for (String prefix : new String[]{"026", "027", "056", "057"}) {
            assertThatCode(() ->
                    MomoNumberValidator.validate("airteltigo", prefix + "1234567", "mobile_provider", "mobile_number"))
                    .doesNotThrowAnyException();
        }
    }

    @Test
    @DisplayName("provider is case-insensitive")
    void provider_is_case_insensitive() {
        assertThatCode(() ->
                MomoNumberValidator.validate("MTN", "0241234567", "mobile_provider", "mobile_number"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("number matching a different provider's prefix is rejected")
    void number_matching_wrong_provider_is_rejected() {
        assertThatThrownBy(() ->
                MomoNumberValidator.validate("mtn", "0201234567", "mobile_provider", "mobile_number"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(UNPROCESSABLE_ENTITY));
    }

    @Test
    @DisplayName("number that's the wrong length is rejected even with a valid prefix")
    void wrong_length_is_rejected() {
        assertThatThrownBy(() ->
                MomoNumberValidator.validate("mtn", "024123456", "mobile_provider", "mobile_number"))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() ->
                MomoNumberValidator.validate("mtn", "02412345678", "mobile_provider", "mobile_number"))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    @DisplayName("unrecognised provider is rejected")
    void unrecognised_provider_is_rejected() {
        assertThatThrownBy(() ->
                MomoNumberValidator.validate("glo", "0241234567", "momo_provider", "destination_momo_number"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(UNPROCESSABLE_ENTITY));
    }

    @Test
    @DisplayName("blank/null provider or number is rejected, error message references the given field names")
    void blank_fields_are_rejected_with_field_specific_messages() {
        assertThatThrownBy(() ->
                MomoNumberValidator.validate(null, "0241234567", "momo_provider", "destination_momo_number"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getReason())
                        .contains("momo_provider"));
        assertThatThrownBy(() ->
                MomoNumberValidator.validate("mtn", null, "momo_provider", "destination_momo_number"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getReason())
                        .contains("destination_momo_number"));
    }
}
