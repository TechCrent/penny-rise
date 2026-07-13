package com.stash.payments.moolre.channel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY;

class MoolreChannelMapperTest {

    @Test
    @DisplayName("forPayment maps mtn/vodafone/airteltigo to 13/6/7")
    void for_payment_maps_known_providers() {
        assertThat(MoolreChannelMapper.forPayment("mtn")).isEqualTo("13");
        assertThat(MoolreChannelMapper.forPayment("vodafone")).isEqualTo("6");
        assertThat(MoolreChannelMapper.forPayment("airteltigo")).isEqualTo("7");
    }

    @Test
    @DisplayName("forTransfer maps mtn/vodafone/airteltigo to 1/6/7")
    void for_transfer_maps_known_providers() {
        assertThat(MoolreChannelMapper.forTransfer("mtn")).isEqualTo("1");
        assertThat(MoolreChannelMapper.forTransfer("vodafone")).isEqualTo("6");
        assertThat(MoolreChannelMapper.forTransfer("airteltigo")).isEqualTo("7");
    }

    @Test
    @DisplayName("provider matching is case-insensitive")
    void provider_is_case_insensitive() {
        assertThat(MoolreChannelMapper.forPayment("MTN")).isEqualTo("13");
        assertThat(MoolreChannelMapper.forTransfer("Vodafone")).isEqualTo("6");
    }

    @Test
    @DisplayName("unknown provider throws 422")
    void unknown_provider_throws_422() {
        assertThatThrownBy(() -> MoolreChannelMapper.forPayment("glo"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(UNPROCESSABLE_ENTITY));

        assertThatThrownBy(() -> MoolreChannelMapper.forTransfer(null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(UNPROCESSABLE_ENTITY));
    }
}
