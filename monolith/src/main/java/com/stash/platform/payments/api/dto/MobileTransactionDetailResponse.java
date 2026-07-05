package com.stash.platform.payments.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.UUID;

/**
 * Mobile-facing transaction detail shape ({@code transaction_reference}, etc.).
 */
public record MobileTransactionDetailResponse(
        @JsonProperty("transaction_reference") String transactionReference,
        @JsonProperty("transaction_type") String transactionType,
        String status,
        @JsonProperty("gross_amount_pesewas") long grossAmountPesewas,
        @JsonProperty("gross_amount_cedis") String grossAmountCedis,
        @JsonProperty("fee_amount_pesewas") long feeAmountPesewas,
        @JsonProperty("net_amount_pesewas") long netAmountPesewas,
        @JsonProperty("external_provider") String externalProvider,
        @JsonProperty("external_reference") String externalReference,
        String narrative,
        @JsonProperty("created_at") String createdAt,
        @JsonProperty("posted_at") String postedAt,
        List<MobileTransactionEntryResponse> entries
) {
    public record MobileTransactionEntryResponse(
            String direction,
            @JsonProperty("amount_pesewas") long amountPesewas,
            @JsonProperty("amount_cedis") String amountCedis,
            @JsonProperty("account_type") String accountType,
            @JsonProperty("account_id") String accountId,
            String narrative
    ) {}

    public record PaymentsTransactionDetail(
            String reference,
            @JsonProperty("transaction_type") String transactionType,
            String status,
            @JsonProperty("gross_amount_pesewas") long grossAmountPesewas,
            @JsonProperty("gross_amount_cedis") String grossAmountCedis,
            @JsonProperty("fee_amount_pesewas") long feeAmountPesewas,
            @JsonProperty("net_amount_pesewas") long netAmountPesewas,
            @JsonProperty("initiating_user_id") UUID initiatingUserId,
            @JsonProperty("counterparty_user_id") UUID counterpartyUserId,
            @JsonProperty("external_provider") String externalProvider,
            @JsonProperty("external_reference") String externalReference,
            @JsonProperty("created_at") String createdAt,
            @JsonProperty("completed_at") String completedAt,
            List<PaymentsTransactionEntry> entries
    ) {}

    public record PaymentsTransactionEntry(
            String direction,
            @JsonProperty("amount_pesewas") long amountPesewas,
            @JsonProperty("amount_cedis") String amountCedis,
            @JsonProperty("account_type") String accountType,
            @JsonProperty("account_id") String accountId,
            String narrative
    ) {}

    public static MobileTransactionDetailResponse from(PaymentsTransactionDetail detail) {
        List<MobileTransactionEntryResponse> entries = detail.entries() == null
                ? List.of()
                : detail.entries().stream()
                        .map(e -> new MobileTransactionEntryResponse(
                                e.direction(),
                                e.amountPesewas(),
                                e.amountCedis(),
                                e.accountType(),
                                e.accountId(),
                                e.narrative()))
                        .toList();

        String narrative = entries.isEmpty() ? null : entries.get(0).narrative();

        return new MobileTransactionDetailResponse(
                detail.reference(),
                detail.transactionType(),
                detail.status(),
                detail.grossAmountPesewas(),
                detail.grossAmountCedis(),
                detail.feeAmountPesewas(),
                detail.netAmountPesewas(),
                detail.externalProvider(),
                detail.externalReference(),
                narrative,
                detail.createdAt(),
                detail.completedAt(),
                entries
        );
    }
}
