package com.stash.platform.transaction.service;

import com.stash.admin.integration.PaymentsTransactionRow;
import com.stash.platform.susu.service.SusuGroupQueryService;
import com.stash.platform.user.service.UserService;
import com.stash.platform.vault.service.VaultService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TransactionHistoryEnricherTest {

    private final VaultService           vaultService          = mock(VaultService.class);
    private final SusuGroupQueryService  susuGroupQueryService = mock(SusuGroupQueryService.class);
    private final UserService            userService           = mock(UserService.class);
    private final TransactionHistoryEnricher enricher =
            new TransactionHistoryEnricher(vaultService, susuGroupQueryService, userService);

    private static final UUID USER_ID       = UUID.randomUUID();
    private static final UUID OTHER_USER_ID = UUID.randomUUID();
    private static final UUID VAULT_ID      = UUID.randomUUID();
    private static final UUID GROUP_ID      = UUID.randomUUID();

    // ── account_name ─────────────────────────────────────────────────────

    @Test
    @DisplayName("no business_reference_type resolves to Wallet")
    void nullBusinessReferenceResolvesToWallet() {
        var row = row("WITHDRAWAL", USER_ID, null, null, null);
        assertThat(enricher.resolveAccountName(row)).isEqualTo("Wallet");
    }

    @Test
    @DisplayName("VAULT_DEPOSIT resolves to the vault's real name")
    void vaultDepositResolvesToVaultName() {
        when(vaultService.getVaultName(VAULT_ID)).thenReturn(Optional.of("Emergency Fund"));
        var row = row("TRANSFER", USER_ID, USER_ID, "VAULT_DEPOSIT", VAULT_ID);

        assertThat(enricher.resolveAccountName(row)).isEqualTo("Emergency Fund");
    }

    @Test
    @DisplayName("SUSU_CONTRIBUTION resolves to the susu group's real name")
    void susuContributionResolvesToGroupName() {
        when(susuGroupQueryService.getGroupName(GROUP_ID)).thenReturn(Optional.of("Legon Roommates Susu"));
        var row = row("SUSU_CONTRIBUTION", USER_ID, null, "SUSU_CONTRIBUTION", GROUP_ID);

        assertThat(enricher.resolveAccountName(row)).isEqualTo("Legon Roommates Susu");
    }

    // ── direction ────────────────────────────────────────────────────────

    @Test
    @DisplayName("WITHDRAWAL is always OUT")
    void withdrawalIsOut() {
        assertThat(enricher.resolveDirection(row("WITHDRAWAL", USER_ID, null, null, null), USER_ID))
                .isEqualTo("OUT");
    }

    @Test
    @DisplayName("DEPOSIT is always IN")
    void depositIsIn() {
        assertThat(enricher.resolveDirection(row("DEPOSIT", USER_ID, null, null, null), USER_ID))
                .isEqualTo("IN");
    }

    @Test
    @DisplayName("TRANSFER sent (this user is initiator, someone else is counterparty) is OUT")
    void transferSentIsOut() {
        var row = row("TRANSFER", USER_ID, OTHER_USER_ID, null, null);
        assertThat(enricher.resolveDirection(row, USER_ID)).isEqualTo("OUT");
    }

    @Test
    @DisplayName("TRANSFER received (someone else is initiator, this user is counterparty) is IN")
    void transferReceivedIsIn() {
        var row = row("TRANSFER", OTHER_USER_ID, USER_ID, null, null);
        assertThat(enricher.resolveDirection(row, USER_ID)).isEqualTo("IN");
    }

    @Test
    @DisplayName("self-transfer wallet→vault (VAULT_DEPOSIT) is OUT")
    void selfTransferToVaultIsOut() {
        var row = row("TRANSFER", USER_ID, USER_ID, "VAULT_DEPOSIT", VAULT_ID);
        assertThat(enricher.resolveDirection(row, USER_ID)).isEqualTo("OUT");
    }

    @Test
    @DisplayName("self-transfer vault→wallet (VAULT_WITHDRAWAL) is IN")
    void selfTransferFromVaultIsIn() {
        var row = row("TRANSFER", USER_ID, USER_ID, "VAULT_WITHDRAWAL", VAULT_ID);
        assertThat(enricher.resolveDirection(row, USER_ID)).isEqualTo("IN");
    }

    @Test
    @DisplayName("SUSU_CONTRIBUTION is OUT, SUSU_DISBURSEMENT is IN")
    void susuDirections() {
        assertThat(enricher.resolveDirection(row("SUSU_CONTRIBUTION", USER_ID, null, null, null), USER_ID))
                .isEqualTo("OUT");
        assertThat(enricher.resolveDirection(row("SUSU_DISBURSEMENT", USER_ID, null, null, null), USER_ID))
                .isEqualTo("IN");
    }

    // ── counterparty_name ────────────────────────────────────────────────

    @Test
    @DisplayName("null counterparty_user_id resolves to null counterparty_name")
    void nullCounterpartyResolvesToNull() {
        var row = row("WITHDRAWAL", USER_ID, null, null, null);
        assertThat(enricher.resolveCounterpartyName(row)).isNull();
    }

    @Test
    @DisplayName("present counterparty_user_id resolves to their display name")
    void presentCounterpartyResolvesToName() {
        when(userService.getDisplayName(OTHER_USER_ID)).thenReturn(Optional.of("Akua Mensah"));
        var row = row("TRANSFER", USER_ID, OTHER_USER_ID, null, null);

        assertThat(enricher.resolveCounterpartyName(row)).isEqualTo("Akua Mensah");
    }

    private PaymentsTransactionRow row(String type, UUID initiator, UUID counterparty,
                                        String businessRefType, UUID businessRefId) {
        return new PaymentsTransactionRow("STSH-202607-000001", type, initiator, counterparty,
                10000, 0, 10000, "COMPLETED", businessRefType, businessRefId, "test", Instant.now());
    }
}
