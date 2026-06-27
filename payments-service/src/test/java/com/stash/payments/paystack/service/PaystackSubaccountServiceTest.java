package com.stash.payments.paystack.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stash.payments.ledger.domain.LedgerAccountEntity;
import com.stash.payments.ledger.repository.LedgerAccountRepository;
import com.stash.payments.paystack.client.PaystackClient;
import com.stash.payments.paystack.domain.PaystackSubaccountEntity;
import com.stash.payments.paystack.dto.SubaccountCreateResponse;
import com.stash.payments.paystack.exception.PaystackServerException;
import com.stash.payments.paystack.repository.PaystackSubaccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PaystackSubaccountServiceTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-06-24T10:00:00Z"), ZoneOffset.UTC);

    private final PaystackClient               paystackClient    = Mockito.mock(PaystackClient.class);
    private final PaystackSubaccountRepository subaccountRepo    = Mockito.mock(PaystackSubaccountRepository.class);
    private final LedgerAccountRepository      ledgerAccountRepo = Mockito.mock(LedgerAccountRepository.class);
    private final PaystackSubaccountService    service =
            new PaystackSubaccountService(paystackClient, subaccountRepo,
                    ledgerAccountRepo, new ObjectMapper(), FIXED_CLOCK);

    private static final UUID   LEDGER_ACCOUNT_ID = UUID.randomUUID();
    private static final UUID   USER_ID            = UUID.randomUUID();
    private static final String EMAIL              = "akua@stash.test";
    private static final String CORR_ID            = "corr-paystack-001";
    private static final String SUBACCOUNT_CODE    = "ACCT_test123abc";

    @BeforeEach
    void setUp() {
        when(subaccountRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(ledgerAccountRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    // ── Happy path ────────────────────────────────────────────────────────

    @Test
    @DisplayName("successful Paystack call stores subaccount and updates ledger external_reference")
    void happy_path_stores_subaccount_and_updates_ledger() {
        when(subaccountRepo.existsByOwnerTypeAndOwnerId("USER", USER_ID)).thenReturn(false);
        when(paystackClient.createSubaccount(any())).thenReturn(successResponse(SUBACCOUNT_CODE));

        LedgerAccountEntity ledgerAccount = new LedgerAccountEntity(
                "USER_WALLET", "USER", USER_ID, "desc", Instant.now(FIXED_CLOCK));
        when(ledgerAccountRepo.findById(LEDGER_ACCOUNT_ID)).thenReturn(Optional.of(ledgerAccount));

        service.provisionSubaccount(LEDGER_ACCOUNT_ID, USER_ID, EMAIL, CORR_ID);

        // Verify subaccount row saved
        ArgumentCaptor<PaystackSubaccountEntity> subCaptor =
                ArgumentCaptor.forClass(PaystackSubaccountEntity.class);
        verify(subaccountRepo).save(subCaptor.capture());
        assertThat(subCaptor.getValue().getPaystackSubaccountCode()).isEqualTo(SUBACCOUNT_CODE);
        assertThat(subCaptor.getValue().getLedgerAccountId()).isEqualTo(LEDGER_ACCOUNT_ID);
        assertThat(subCaptor.getValue().getOwnerType()).isEqualTo("USER");
        assertThat(subCaptor.getValue().getOwnerId()).isEqualTo(USER_ID);

        // Verify ledger account external_reference updated
        verify(ledgerAccountRepo).save(argThat(acc ->
                SUBACCOUNT_CODE.equals(acc.getExternalReference())));
    }

    // ── Idempotency ───────────────────────────────────────────────────────

    @Test
    @DisplayName("already provisioned: no Paystack call, no writes")
    void already_provisioned_is_noop() {
        when(subaccountRepo.existsByOwnerTypeAndOwnerId("USER", USER_ID)).thenReturn(true);

        service.provisionSubaccount(LEDGER_ACCOUNT_ID, USER_ID, EMAIL, CORR_ID);

        verifyNoInteractions(paystackClient);
        verify(subaccountRepo, never()).save(any());
        verify(ledgerAccountRepo, never()).save(any());
    }

    // ── Paystack failure path ─────────────────────────────────────────────

    @Test
    @DisplayName("Paystack 5xx throws — consumer will requeue")
    void paystack_5xx_propagates_for_requeue() {
        when(subaccountRepo.existsByOwnerTypeAndOwnerId("USER", USER_ID)).thenReturn(false);
        when(paystackClient.createSubaccount(any()))
                .thenThrow(new PaystackServerException("Gateway error", 500));

        assertThatThrownBy(() ->
                service.provisionSubaccount(LEDGER_ACCOUNT_ID, USER_ID, EMAIL, CORR_ID))
                .isInstanceOf(PaystackServerException.class);

        // Nothing persisted — no partial state
        verify(subaccountRepo, never()).save(any());
        verify(ledgerAccountRepo, never()).save(any());
    }

    @Test
    @DisplayName("Paystack returns status=false — throws PaystackSubaccountCreationException")
    void paystack_status_false_throws() {
        when(subaccountRepo.existsByOwnerTypeAndOwnerId("USER", USER_ID)).thenReturn(false);
        when(paystackClient.createSubaccount(any()))
                .thenReturn(new SubaccountCreateResponse(false, "Account number invalid", null));

        assertThatThrownBy(() ->
                service.provisionSubaccount(LEDGER_ACCOUNT_ID, USER_ID, EMAIL, CORR_ID))
                .isInstanceOf(PaystackSubaccountCreationException.class)
                .hasMessageContaining("status=false");

        verify(subaccountRepo, never()).save(any());
    }

    // ── Helper ────────────────────────────────────────────────────────────

    private static SubaccountCreateResponse successResponse(String code) {
        return new SubaccountCreateResponse(
                true, "Subaccount created",
                new SubaccountCreateResponse.SubaccountData(
                        code, "Stash/akua@stash.test", "TEST", "0000000000"));
    }
}
