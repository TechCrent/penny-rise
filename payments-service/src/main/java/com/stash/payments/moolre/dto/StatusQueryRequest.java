package com.stash.payments.moolre.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record StatusQueryRequest(
        int type,
        @JsonProperty("idtype") String idType,
        String id,
        @JsonProperty("accountnumber") String accountNumber
) {
    /** Query by unique externalref ({@code idtype=1}). */
    public static StatusQueryRequest byExternalRef(String externalRef, String accountNumber) {
        return new StatusQueryRequest(1, "1", externalRef, accountNumber);
    }
}
