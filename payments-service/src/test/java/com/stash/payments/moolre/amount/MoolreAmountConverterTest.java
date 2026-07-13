package com.stash.payments.moolre.amount;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoolreAmountConverterTest {

    @Test
    @DisplayName("pesewasToGhsString formats two decimal places")
    void pesewas_to_ghs_string() {
        assertThat(MoolreAmountConverter.pesewasToGhsString(2000)).isEqualTo("20.00");
        assertThat(MoolreAmountConverter.pesewasToGhsString(1)).isEqualTo("0.01");
        assertThat(MoolreAmountConverter.pesewasToGhsString(0)).isEqualTo("0.00");
    }

    @Test
    @DisplayName("ghsStringToPesewas multiplies by 100")
    void ghs_string_to_pesewas() {
        assertThat(MoolreAmountConverter.ghsStringToPesewas("20.00")).isEqualTo(2000L);
        assertThat(MoolreAmountConverter.ghsStringToPesewas("0.01")).isEqualTo(1L);
        assertThat(MoolreAmountConverter.ghsStringToPesewas("5")).isEqualTo(500L);
    }

    @Test
    @DisplayName("blank amount throws")
    void blank_amount_throws() {
        assertThatThrownBy(() -> MoolreAmountConverter.ghsStringToPesewas(" "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
