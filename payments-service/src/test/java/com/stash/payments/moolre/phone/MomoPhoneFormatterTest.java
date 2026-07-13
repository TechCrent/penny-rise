package com.stash.payments.moolre.phone;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MomoPhoneFormatterTest {

    @Test
    @DisplayName("local 0XXXXXXXXX becomes 233XXXXXXXXX")
    void local_number_gets_country_code() {
        assertThat(MomoPhoneFormatter.toInternational("0241234567"))
                .isEqualTo("233241234567");
    }

    @Test
    @DisplayName("already-international 233... is returned as-is")
    void already_international_unchanged() {
        assertThat(MomoPhoneFormatter.toInternational("233241234567"))
                .isEqualTo("233241234567");
    }

    @Test
    @DisplayName("+233 prefix has the plus stripped")
    void plus_prefix_stripped() {
        assertThat(MomoPhoneFormatter.toInternational("+233241234567"))
                .isEqualTo("233241234567");
    }

    @Test
    @DisplayName("blank or unsupported format throws")
    void blank_or_unsupported_throws() {
        assertThatThrownBy(() -> MomoPhoneFormatter.toInternational(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MomoPhoneFormatter.toInternational("241234567"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
