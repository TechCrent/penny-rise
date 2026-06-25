package com.stash.payments.ledger.service;

import com.stash.payments.ledger.domain.LedgerAccountEntity;
import com.stash.payments.ledger.repository.LedgerAccountRepository;
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
import static org.mockito.Mockito.*;

class UserWalletProvisioningServiceTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-06-24T10:00:00Z"), ZoneOffset.UTC);

    private final LedgerAccountRepository repo =
            Mockito.mock(LedgerAccountRepository.class);
    private final UserWalletProvisioningService service =
            new UserWalletProvisioningService(repo, FIXED_CLOCK);

    private static final UUID   USER_ID = UUID.randomUUID();
    private static final String CORR_ID = "corr-provision-001";

    @BeforeEach
    void setUp() {
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    // ── Happy path ────────────────────────────────────────────────────────

    @Test
    @DisplayName("provisions USER_WALLET with correct fields on first call")
    void provisions_user_wallet_on_first_call() {
        when(repo.findByOwnerTypeAndOwnerIdAndAccountType("USER", USER_ID, "USER_WALLET"))
                .thenReturn(Optional.empty());

        service.provisionWallet(USER_ID, CORR_ID);

        ArgumentCaptor<LedgerAccountEntity> captor =
                ArgumentCaptor.forClass(LedgerAccountEntity.class);
        verify(repo).save(captor.capture());

        LedgerAccountEntity saved = captor.getValue();
        assertThat(saved.getAccountType()).isEqualTo("USER_WALLET");
        assertThat(saved.getOwnerType()).isEqualTo("USER");
        assertThat(saved.getOwnerId()).isEqualTo(USER_ID);
        assertThat(saved.getStatus()).isEqualTo("ACTIVE");
    }

    // ── Idempotency ───────────────────────────────────────────────────────

    @Test
    @DisplayName("duplicate event returns existing account without creating a new one")
    void idempotent_on_duplicate_event() {
        LedgerAccountEntity existing = new LedgerAccountEntity(
                "USER_WALLET", "USER", USER_ID, "existing", Instant.now(FIXED_CLOCK));
        when(repo.findByOwnerTypeAndOwnerIdAndAccountType("USER", USER_ID, "USER_WALLET"))
                .thenReturn(Optional.of(existing));

        LedgerAccountEntity result = service.provisionWallet(USER_ID, CORR_ID);

        verify(repo, never()).save(any());
        assertThat(result).isSameAs(existing);
    }

    @Test
    @DisplayName("duplicate event does not throw — consumer acknowledges cleanly")
    void duplicate_event_does_not_throw() {
        LedgerAccountEntity existing = new LedgerAccountEntity(
                "USER_WALLET", "USER", USER_ID, "existing", Instant.now(FIXED_CLOCK));
        when(repo.findByOwnerTypeAndOwnerIdAndAccountType("USER", USER_ID, "USER_WALLET"))
                .thenReturn(Optional.of(existing));

        assertThatCode(() -> service.provisionWallet(USER_ID, CORR_ID))
                .doesNotThrowAnyException();
    }
}
