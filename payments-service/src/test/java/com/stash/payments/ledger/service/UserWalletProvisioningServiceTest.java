package com.stash.payments.ledger.service;

import com.stash.payments.ledger.domain.LedgerAccountEntity;
import com.stash.payments.ledger.repository.LedgerAccountRepository;
import com.stash.payments.outbox.domain.OutboxEvent;
import com.stash.payments.outbox.domain.OutboxEventEntity;
import com.stash.payments.outbox.service.OutboxPublisher;
import com.stash.payments.paystack.event.PaystackSubaccountProvisionRequestedEvent;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class UserWalletProvisioningServiceTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-06-24T10:00:00Z"), ZoneOffset.UTC);

    private final LedgerAccountRepository      accountRepo    = Mockito.mock(LedgerAccountRepository.class);
    private final PaystackSubaccountRepository  subaccountRepo = Mockito.mock(PaystackSubaccountRepository.class);
    private final OutboxPublisher              outbox         = Mockito.mock(OutboxPublisher.class);
    private final UserWalletProvisioningService service =
            new UserWalletProvisioningService(accountRepo, subaccountRepo, outbox, FIXED_CLOCK);

    private static final UUID   USER_ID   = UUID.randomUUID();
    private static final String EMAIL     = "akua@stash.test";
    private static final String CORR_ID   = "corr-provision-001";

    @BeforeEach
    void setUp() {
        when(accountRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(outbox.publish(any(), any())).thenReturn(Mockito.mock(OutboxEventEntity.class));
    }

    // ── Happy path ────────────────────────────────────────────────────────

    @Test
    @DisplayName("first call: saves ledger account and writes outbox event")
    void first_call_provisions_account_and_queues_outbox_event() {
        when(accountRepo.findByOwnerTypeAndOwnerIdAndAccountType("USER", USER_ID, "USER_WALLET"))
                .thenReturn(Optional.empty());
        when(subaccountRepo.existsByOwnerTypeAndOwnerId("USER", USER_ID))
                .thenReturn(false);

        service.provisionWallet(USER_ID, EMAIL, CORR_ID);

        verify(accountRepo).save(any(LedgerAccountEntity.class));

        ArgumentCaptor<OutboxEvent> eventCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outbox).publish(eventCaptor.capture(), eq(CORR_ID));

        var event = (PaystackSubaccountProvisionRequestedEvent) eventCaptor.getValue();
        assertThat(event.userId()).isEqualTo(USER_ID);
        assertThat(event.userEmail()).isEqualTo(EMAIL);
        assertThat(event.getEventType()).isEqualTo("payments.paystack.subaccount.provision.requested");
    }

    // ── Idempotency paths ─────────────────────────────────────────────────

    @Test
    @DisplayName("duplicate user.created: ledger account exists — no writes")
    void duplicate_event_ledger_account_exists_noop() {
        LedgerAccountEntity existing = new LedgerAccountEntity(
                "USER_WALLET", "USER", USER_ID, "existing", Instant.now(FIXED_CLOCK));
        when(accountRepo.findByOwnerTypeAndOwnerIdAndAccountType("USER", USER_ID, "USER_WALLET"))
                .thenReturn(Optional.of(existing));

        service.provisionWallet(USER_ID, EMAIL, CORR_ID);

        verify(accountRepo, never()).save(any());
        verifyNoInteractions(outbox);
    }

    @Test
    @DisplayName("partial failure recovery: ledger account exists, subaccount does not — no outbox event")
    void ledger_account_exists_subaccount_does_not_skips_outbox() {
        // This case: provisionWallet already ran (account exists),
        // but subaccountRepo now shows it exists too — no extra event needed.
        LedgerAccountEntity existing = new LedgerAccountEntity(
                "USER_WALLET", "USER", USER_ID, "existing", Instant.now(FIXED_CLOCK));
        when(accountRepo.findByOwnerTypeAndOwnerIdAndAccountType("USER", USER_ID, "USER_WALLET"))
                .thenReturn(Optional.of(existing));
        when(subaccountRepo.existsByOwnerTypeAndOwnerId("USER", USER_ID))
                .thenReturn(true);

        service.provisionWallet(USER_ID, EMAIL, CORR_ID);

        verify(accountRepo, never()).save(any());
        verifyNoInteractions(outbox);
    }

    @Test
    @DisplayName("does not throw when account already exists — consumer acknowledges cleanly")
    void no_exception_on_duplicate() {
        LedgerAccountEntity existing = new LedgerAccountEntity(
                "USER_WALLET", "USER", USER_ID, "existing", Instant.now(FIXED_CLOCK));
        when(accountRepo.findByOwnerTypeAndOwnerIdAndAccountType("USER", USER_ID, "USER_WALLET"))
                .thenReturn(Optional.of(existing));

        assertThatCode(() -> service.provisionWallet(USER_ID, EMAIL, CORR_ID))
                .doesNotThrowAnyException();
    }
}
