package com.stash.platform.transaction.service;

import com.stash.admin.integration.PaymentsTransactionRow;
import com.stash.platform.susu.service.SusuGroupQueryService;
import com.stash.platform.user.service.UserService;
import com.stash.platform.vault.service.VaultService;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Enriches raw Payments transaction rows with account_name, direction, and
 * counterparty_name — three fields that Payments can't supply itself because
 * vault/susu names live in the monolith and counterparty display names are in
 * user_module.
 *
 * <p>DIRECTION RULES:
 * <ul>
 *   <li>WITHDRAWAL — always OUT</li>
 *   <li>DEPOSIT (external, Paystack) — always IN</li>
 *   <li>SUSU_CONTRIBUTION — always OUT (wallet -> pot)</li>
 *   <li>SUSU_DISBURSEMENT (this user is recipient) — always IN</li>
 *   <li>TRANSFER where this user is initiator and counterparty is someone else — OUT (sent)</li>
 *   <li>TRANSFER where this user is counterparty and initiator is someone else — IN (received)</li>
 *   <li>Self-transfer (wallet ↔ vault, initiator == counterparty == this user) — disambiguated
 *       via business_reference_type: VAULT_DEPOSIT = OUT (wallet → vault), VAULT_WITHDRAWAL = IN
 *       (vault → wallet). This is a judgment call — "net effect on the user's overall position"
 *       doesn't change for a same-user internal move, so the direction reflects the flow
 *       from the wallet's perspective (the debited account).</li>
 * </ul>
 */
@Component
public class TransactionHistoryEnricher {

    private final VaultService           vaultService;
    private final SusuGroupQueryService  susuGroupQueryService;
    private final UserService            userService;

    public TransactionHistoryEnricher(VaultService vaultService,
                                       SusuGroupQueryService susuGroupQueryService,
                                       UserService userService) {
        this.vaultService          = vaultService;
        this.susuGroupQueryService = susuGroupQueryService;
        this.userService           = userService;
    }

    public String resolveAccountName(PaymentsTransactionRow row) {
        if (row.businessReferenceType() == null) return "Wallet";
        return switch (row.businessReferenceType()) {
            case "VAULT_DEPOSIT", "VAULT_WITHDRAWAL" ->
                    vaultService.getVaultName(row.businessReferenceId()).orElse("Vault");
            case "SUSU_CONTRIBUTION", "SUSU_DISBURSEMENT" ->
                    susuGroupQueryService.getGroupName(row.businessReferenceId()).orElse("Susu Group");
            default -> "Wallet";
        };
    }

    public String resolveDirection(PaymentsTransactionRow row, UUID queryingUserId) {
        return switch (row.transactionType()) {
            case "WITHDRAWAL"       -> "OUT";
            case "DEPOSIT"          -> "IN";
            case "SUSU_CONTRIBUTION"-> "OUT";
            case "SUSU_DISBURSEMENT"-> "IN";
            case "TRANSFER"         -> resolveTransferDirection(row, queryingUserId);
            // Conservative default for any unrecognised type — OUT avoids accidentally
            // showing a user a credit that hasn't actually arrived.
            default -> "OUT";
        };
    }

    private String resolveTransferDirection(PaymentsTransactionRow row, UUID queryingUserId) {
        boolean isInitiator    = queryingUserId.equals(row.initiatingUserId());
        boolean isCounterparty = queryingUserId.equals(row.counterpartyUserId());

        if (isInitiator && !isCounterparty)  return "OUT"; // sent to someone else
        if (isCounterparty && !isInitiator)  return "IN";  // received from someone else

        // Self-transfer (wallet ↔ vault): both roles are this user, disambiguate by
        // business_reference_type — VAULT_DEPOSIT is wallet → vault (OUT from wallet),
        // VAULT_WITHDRAWAL is vault → wallet (IN to wallet).
        if ("VAULT_DEPOSIT".equals(row.businessReferenceType()))    return "OUT";
        if ("VAULT_WITHDRAWAL".equals(row.businessReferenceType())) return "IN";
        return "OUT"; // fallback for an unrecognised self-transfer shape
    }

    public String resolveCounterpartyName(PaymentsTransactionRow row) {
        if (row.counterpartyUserId() == null) return null;
        // Uses findByIdIncludingDeleted so historical transactions from deleted accounts
        // still show the counterparty name rather than null.
        return userService.getDisplayName(row.counterpartyUserId()).orElse(null);
    }
}
