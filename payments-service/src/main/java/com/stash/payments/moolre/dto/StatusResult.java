package com.stash.payments.moolre.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@JsonIgnoreProperties(ignoreUnknown = true)
public record StatusResult(
        String code,
        String message,
        boolean success,
        Integer txStatus,
        Integer txType,
        String amount,
        String value,
        @JsonProperty("transactionid") String transactionId,
        @JsonProperty("externalref") String externalRef,
        @JsonProperty("thirdpartyref") String thirdPartyRef,
        String payer,
        String payee,
        String ts
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StatusData(
            @JsonProperty("txstatus") Integer txStatus,
            @JsonProperty("txtype") Integer txType,
            String amount,
            String value,
            @JsonProperty("transactionid") String transactionId,
            @JsonProperty("externalref") String externalRef,
            @JsonProperty("thirdpartyref") String thirdPartyRef,
            String payer,
            String payee,
            String ts
    ) {}

    public static StatusResult from(MoolreEnvelope envelope, ObjectMapper mapper) {
        StatusData data = parseData(envelope.data(), mapper);
        if (data == null) {
            return new StatusResult(
                    envelope.code(), envelope.messageAsText(), envelope.isSuccess(),
                    null, null, null, null, null, null, null, null, null, null);
        }
        return new StatusResult(
                envelope.code(),
                envelope.messageAsText(),
                envelope.isSuccess(),
                data.txStatus(),
                data.txType(),
                data.amount(),
                data.value(),
                data.transactionId(),
                data.externalRef(),
                data.thirdPartyRef(),
                data.payer(),
                data.payee(),
                data.ts());
    }

    private static StatusData parseData(JsonNode data, ObjectMapper mapper) {
        if (data == null || data.isNull() || !data.isObject()) {
            return null;
        }
        return mapper.convertValue(data, StatusData.class);
    }
}
