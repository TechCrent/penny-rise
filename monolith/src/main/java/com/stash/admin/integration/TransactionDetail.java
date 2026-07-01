package com.stash.admin.integration;

import java.util.UUID;

public record TransactionDetail(UUID transactionId, UUID userId, String reference,
                                 String type, long amountPesewas, String status) {}
