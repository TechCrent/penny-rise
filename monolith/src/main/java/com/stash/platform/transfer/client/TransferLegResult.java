package com.stash.platform.transfer.client;

import java.util.UUID;

/** Result of a Payments Service internal transfer leg. */
public record TransferLegResult(String transactionReference, UUID ledgerTransactionId) {}
