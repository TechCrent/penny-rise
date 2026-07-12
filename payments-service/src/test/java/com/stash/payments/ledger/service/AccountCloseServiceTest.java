package com.stash.payments.ledger.service;

import com.stash.payments.ledger.domain.LedgerAccountEntity;
import com.stash.payments.ledger.exception.AccountBalanceNotZeroException;
import com.stash.payments.ledger.repository.LedgerAccountRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.NOT_FOUND;

class AccountCloseServiceTest {

    private static final UUID ACCOUNT_ID = UUID.randomUUID();

    private final LedgerAccountRepository repo           = Mockito.mock(LedgerAccountRepository.class);
    private final BalanceService           balanceService = Mockito.mock(BalanceService.class);
    private final AccountCloseService      service        = new AccountCloseService(repo, balanceService);

    // ── Happy path ───────────────────────────────────────────────────────

    @Test
    @DisplayName("closes an ACTIVE account with zero balance")
    void closes_active_zero_balance_account() {
        LedgerAccountEntity account = activeAccount();
        when(repo.findById(ACCOUNT_ID)).thenReturn(Optional.of(account));
        when(balanceService.computeBalanceFast(ACCOUNT_ID)).thenReturn(0L);

        service.close(ACCOUNT_ID);

        assertThat(account.getStatus()).isEqualTo("CLOSED");
    }

    // ── Idempotency ──────────────────────────────────────────────────────

    @Test
    @DisplayName("closing an already-CLOSED account is a no-op, not an error")
    void already_closed_is_noop() {
        LedgerAccountEntity account = closedAccount();
        when(repo.findById(ACCOUNT_ID)).thenReturn(Optional.of(account));

        assertThatCode(() -> service.close(ACCOUNT_ID)).doesNotThrowAnyException();

        verifyNoInteractions(balanceService);
    }

    // ── Non-zero balance rejection ──────────────────────────────────────

    @Test
    @DisplayName("rejects closing an account with a non-zero balance")
    void rejects_nonzero_balance() {
        LedgerAccountEntity account = activeAccount();
        when(repo.findById(ACCOUNT_ID)).thenReturn(Optional.of(account));
        when(balanceService.computeBalanceFast(ACCOUNT_ID)).thenReturn(500L);

        assertThatThrownBy(() -> service.close(ACCOUNT_ID))
                .isInstanceOf(AccountBalanceNotZeroException.class)
                .satisfies(ex -> {
                    var e = (AccountBalanceNotZeroException) ex;
                    assertThat(e.getAccountId()).isEqualTo(ACCOUNT_ID);
                    assertThat(e.getBalance()).isEqualTo(500L);
                });

        assertThat(account.getStatus()).isEqualTo("ACTIVE");
    }

    // ── Non-existent account ─────────────────────────────────────────────

    @Test
    @DisplayName("non-existent account returns 404")
    void nonexistent_account_returns_404() {
        when(repo.findById(ACCOUNT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.close(ACCOUNT_ID))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(NOT_FOUND));

        verifyNoInteractions(balanceService);
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private static LedgerAccountEntity activeAccount() {
        return new LedgerAccountEntity("VAULT", "VAULT", UUID.randomUUID(), "vault", Instant.now());
    }

    private static LedgerAccountEntity closedAccount() {
        LedgerAccountEntity account = activeAccount();
        account.close();
        return account;
    }
}
