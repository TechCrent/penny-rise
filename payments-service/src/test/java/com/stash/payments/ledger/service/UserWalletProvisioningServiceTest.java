package com.stash.payments.ledger.service;

import com.stash.payments.ledger.domain.LedgerAccountEntity;
import com.stash.payments.ledger.repository.LedgerAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class UserWalletProvisioningServiceTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-06-24T10:00:00Z"), ZoneOffset.UTC);

    private final LedgerAccountRepository accountRepo = Mockito.mock(LedgerAccountRepository.class);
    private final UserWalletProvisioningService service =
            new UserWalletProvisioningService(accountRepo, FIXED_CLOCK);

    private static final UUID   USER_ID = UUID.randomUUID();
    private static final String EMAIL   = "akua@stash.test";
    private static final String CORR_ID = "corr-provision-001";

    @BeforeEach
    void setUp() {
        when(accountRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("first call: saves ledger account without Paystack outbox event")
    void first_call_provisions_account_only() {
        when(accountRepo.findByOwnerTypeAndOwnerIdAndAccountType("USER", USER_ID, "USER_WALLET"))
                .thenReturn(Optional.empty());

        LedgerAccountEntity saved = service.provisionWallet(USER_ID, EMAIL, CORR_ID);

        verify(accountRepo).save(any(LedgerAccountEntity.class));
        assertThat(saved.getOwnerId()).isEqualTo(USER_ID);
        assertThat(saved.getAccountType()).isEqualTo("USER_WALLET");
    }

    @Test
    @DisplayName("duplicate user.created: ledger account exists — no writes")
    void duplicate_event_ledger_account_exists_noop() {
        LedgerAccountEntity existing = new LedgerAccountEntity(
                "USER_WALLET", "USER", USER_ID, "existing", Instant.now(FIXED_CLOCK));
        when(accountRepo.findByOwnerTypeAndOwnerIdAndAccountType("USER", USER_ID, "USER_WALLET"))
                .thenReturn(Optional.of(existing));

        service.provisionWallet(USER_ID, EMAIL, CORR_ID);

        verify(accountRepo, never()).save(any());
    }

    @Test
    @DisplayName("does not throw when account already exists")
    void no_exception_on_duplicate() {
        LedgerAccountEntity existing = new LedgerAccountEntity(
                "USER_WALLET", "USER", USER_ID, "existing", Instant.now(FIXED_CLOCK));
        when(accountRepo.findByOwnerTypeAndOwnerIdAndAccountType("USER", USER_ID, "USER_WALLET"))
                .thenReturn(Optional.of(existing));

        assertThatCode(() -> service.provisionWallet(USER_ID, EMAIL, CORR_ID))
                .doesNotThrowAnyException();
    }
}
