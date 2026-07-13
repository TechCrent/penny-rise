package com.stash.payments.moolre.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TransferResult(
        String code,
        String message,
        boolean success,
        Integer txStatus,
        String receiver,
        @JsonProperty("transactionid") String transactionId,
        @JsonProperty("externalref") String externalRef,
        @JsonProperty("thirdpartyref") String thirdPartyRef,
        @JsonProperty("receivername") String receiverName,
        String amount,
        @JsonProperty("amountfee") String amountFee,
        @JsonProperty("networkfee") String networkFee,
        String fee
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TransferData(
            @JsonProperty("txstatus") Integer txStatus,
            String receiver,
            @JsonProperty("transactionid") String transactionId,
            @JsonProperty("externalref") String externalRef,
            @JsonProperty("thirdpartyref") String thirdPartyRef,
            @JsonProperty("receivername") String receiverName,
            String amount,
            @JsonProperty("amountfee") String amountFee,
            @JsonProperty("networkfee") String networkFee,
            String fee
    ) {}

    public static TransferResult from(MoolreEnvelope envelope, ObjectMapper mapper) {
        TransferData data = parseData(envelope.data(), mapper);
        if (data == null) {
            return new TransferResult(
                    envelope.code(), envelope.messageAsText(), envelope.isSuccess(),
                    null, null, null, null, null, null, null, null, null, null);
        }
        return new TransferResult(
                envelope.code(),
                envelope.messageAsText(),
                envelope.isSuccess(),
                data.txStatus(),
                data.receiver(),
                data.transactionId(),
                data.externalRef(),
                data.thirdPartyRef(),
                data.receiverName(),
                data.amount(),
                data.amountFee(),
                data.networkFee(),
                data.fee());
    }

    private static TransferData parseData(JsonNode data, ObjectMapper mapper) {
        if (data == null || data.isNull() || !data.isObject()) {
            return null;
        }
        return mapper.convertValue(data, TransferData.class);
    }
}
