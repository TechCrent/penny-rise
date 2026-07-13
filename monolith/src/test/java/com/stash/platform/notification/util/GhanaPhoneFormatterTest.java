package com.stash.platform.notification.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("GhanaPhoneFormatter")
class GhanaPhoneFormatterTest {

    @Test
    @DisplayName("E.164 +233… → 233…")
    void e164_to_moolre() {
        assertThat(GhanaPhoneFormatter.toMoolreRecipient("+233501234567"))
                .contains("233501234567");
    }

    @Test
    @DisplayName("already 233… is unchanged")
    void already_moolre_format() {
        assertThat(GhanaPhoneFormatter.toMoolreRecipient("233501234567"))
                .contains("233501234567");
    }

    @Test
    @DisplayName("local 0… → 233…")
    void local_to_moolre() {
        assertThat(GhanaPhoneFormatter.toMoolreRecipient("0501234567"))
                .contains("233501234567");
    }

    @Test
    @DisplayName("blank / invalid → empty")
    void blank_and_invalid() {
        assertThat(GhanaPhoneFormatter.toMoolreRecipient(null)).isEmpty();
        assertThat(GhanaPhoneFormatter.toMoolreRecipient("")).isEmpty();
        assertThat(GhanaPhoneFormatter.toMoolreRecipient("12345")).isEmpty();
    }
}
