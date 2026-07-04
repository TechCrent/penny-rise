package com.stash.kyc.shared.crypto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GhanaCardEncryptionConverterTest {

    @Test
    @DisplayName("converter returns placeholder for unreadable encrypted values")
    void converter_returns_placeholder_for_unreadable_values() {
        var converter = new GhanaCardEncryptionConverter();

        assertThat(converter.convertToEntityAttribute("not-valid-base64")).isEqualTo("[unable to decrypt]");
    }
}
