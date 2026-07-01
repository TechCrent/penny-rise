package com.stash.shared.masking;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("GhanaCardMasker")
class GhanaCardMaskerTest {

    @Test
    @DisplayName("masks a standard Ghana Card showing only the last four alphanumeric characters")
    void masksStandardCard() {
        String result = GhanaCardMasker.maskToLastFour("GHA-123456789-0");
        assertThat(result).isEqualTo("••••••••7890");
    }

    @Test
    @DisplayName("null input returns null")
    void nullReturnsNull() {
        assertThat(GhanaCardMasker.maskToLastFour(null)).isNull();
    }

    @Test
    @DisplayName("blank input returns the input unchanged")
    void blankReturnsBlank() {
        assertThat(GhanaCardMasker.maskToLastFour("   ")).isEqualTo("   ");
    }

    @Test
    @DisplayName("string with four or fewer alphanumeric chars returns only the mask prefix")
    void shortStringReturnsMaskOnly() {
        assertThat(GhanaCardMasker.maskToLastFour("AB12")).isEqualTo("••••••••");
        assertThat(GhanaCardMasker.maskToLastFour("GH-1")).isEqualTo("••••••••");
    }

    @Test
    @DisplayName("masked output always starts with the fixed-length prefix regardless of input length")
    void prefixIsAlwaysFixed() {
        String short5 = GhanaCardMasker.maskToLastFour("ABCDE");
        String long20 = GhanaCardMasker.maskToLastFour("GHA-000000000-000000");

        assertThat(short5).startsWith("••••••••");
        assertThat(long20).startsWith("••••••••");
        assertThat(short5.length()).isEqualTo(long20.length());
    }
}
