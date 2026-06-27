package com.stash.payments.ledger.service;

import com.stash.payments.ledger.api.dto.BalanceResponse;
import com.stash.payments.ledger.domain.LedgerAccountEntity;
import com.stash.payments.ledger.repository.LedgerAccountRepository;
import com.stash.payments.shared.security.CallerContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.web.server.ResponseStatusException;

import java.time.*;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.http.HttpStatus.*;

class AccountBalanceServiceTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-06-24T10:00:00Z"), ZoneOffset.UTC);

    private final LedgerAccountRepository repo          = Mockito.mock(LedgerAccountRepository.class);
    private final BalanceService           balanceService = Mockito.mock(BalanceService.class);
    private final AccountBalanceService    service =
            new AccountBalanceService(repo, balanceService, FIXED_CLOCK);

    private static final UUID ACCOUNT_ID = UUID.randomUUID();
    private static final UUID USER_ID    = UUID.randomUUID();
    private static final UUID OTHER_USER = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(balanceService.computeBalanceFast(ACCOUNT_ID)).thenReturn(125_50L);
    }

    // ── Happy path: user caller ───────────────────────────────────────────

    @Test
    @DisplayName("user caller queries their own USER_WALLET — returns balance")
    void user_queries_own_wallet_returns_balance() {
        when(repo.findById(ACCOUNT_ID)).thenReturn(Optional.of(wallet(USER_ID)));

        BalanceResponse result = service.getBalance(ACCOUNT_ID, CallerContext.user(USER_ID));

        assertThat(result.accountId()).isEqualTo(ACCOUNT_ID);
        assertThat(result.balancePesewas()).isEqualTo(125_50L);
        assertThat(result.balanceCedis()).isEqualTo("125.50");
        assertThat(result.status()).isEqualTo("ACTIVE");
        assertThat(result.accountType()).isEqualTo("USER_WALLET");
        assertThat(result.asOf()).isEqualTo(Instant.parse("2026-06-24T10:00:00Z"));
    }

    // ── Happy path: internal caller ───────────────────────────────────────

    @Test
    @DisplayName("internal caller may query any account type")
    void internal_caller_queries_any_account() {
        when(repo.findById(ACCOUNT_ID)).thenReturn(Optional.of(systemAccount()));

        // Would be 404 for user callers; internal caller gets the balance
        assertThatCode(() -> service.getBalance(ACCOUNT_ID, CallerContext.internal()))
                .doesNotThrowAnyException();

        verify(balanceService).computeBalanceFast(ACCOUNT_ID);
    }

    @Test
    @DisplayName("internal caller: VAULT account returns balance without ownership check")
    void internal_caller_queries_vault() {
        when(repo.findById(ACCOUNT_ID)).thenReturn(Optional.of(vaultAccount()));

        BalanceResponse result = service.getBalance(ACCOUNT_ID, CallerContext.internal());

        assertThat(result.accountType()).isEqualTo("VAULT");
        assertThat(result.balancePesewas()).isEqualTo(125_50L);
    }

    // ── Zero balance ──────────────────────────────────────────────────────

    @Test
    @DisplayName("zero balance returns 0 pesewas and 0.00 cedis — no float precision issues")
    void zero_balance() {
        when(repo.findById(ACCOUNT_ID)).thenReturn(Optional.of(wallet(USER_ID)));
        when(balanceService.computeBalanceFast(ACCOUNT_ID)).thenReturn(0L);

        BalanceResponse result = service.getBalance(ACCOUNT_ID, CallerContext.user(USER_ID));

        assertThat(result.balancePesewas()).isEqualTo(0L);
        assertThat(result.balanceCedis()).isEqualTo("0.00");
    }

    // ── Large balance (no float precision issues) ─────────────────────────

    @Test
    @DisplayName("large balance (100M GHS) stored as BIGINT — no precision loss")
    void large_balance_no_precision_loss() {
        long oneHundredMillionGhs = 100_000_000_00L;  // 100M GHS in pesewas
        when(repo.findById(ACCOUNT_ID)).thenReturn(Optional.of(wallet(USER_ID)));
        when(balanceService.computeBalanceFast(ACCOUNT_ID)).thenReturn(oneHundredMillionGhs);

        BalanceResponse result = service.getBalance(ACCOUNT_ID, CallerContext.user(USER_ID));

        assertThat(result.balancePesewas()).isEqualTo(oneHundredMillionGhs);
        assertThat(result.balanceCedis()).isEqualTo("100000000.00");
        // Crucially: no scientific notation, no rounding, exact string
    }

    // ── Ownership enforcement (user callers) ──────────────────────────────

    @Test
    @DisplayName("user querying another user's wallet returns 404 (not 403 — no enumeration)")
    void wrong_user_gets_404_not_403() {
        when(repo.findById(ACCOUNT_ID)).thenReturn(Optional.of(wallet(OTHER_USER)));

        assertThatThrownBy(() ->
                service.getBalance(ACCOUNT_ID, CallerContext.user(USER_ID)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    var e = (ResponseStatusException) ex;
                    assertThat(e.getStatusCode()).isEqualTo(NOT_FOUND);
                    // Error message must not reveal the account exists
                    assertThat(e.getReason()).doesNotContain("owner");
                    assertThat(e.getReason()).doesNotContain("mismatch");
                });
    }

    @Test
    @DisplayName("user querying VAULT account directly returns 404 (must go via monolith)")
    void user_queries_vault_directly_gets_404() {
        when(repo.findById(ACCOUNT_ID)).thenReturn(Optional.of(vaultAccount()));

        assertThatThrownBy(() ->
                service.getBalance(ACCOUNT_ID, CallerContext.user(USER_ID)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(NOT_FOUND));
    }

    @Test
    @DisplayName("user querying SYSTEM account returns 404")
    void user_queries_system_account_gets_404() {
        when(repo.findById(ACCOUNT_ID)).thenReturn(Optional.of(systemAccount()));

        assertThatThrownBy(() ->
                service.getBalance(ACCOUNT_ID, CallerContext.user(USER_ID)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(NOT_FOUND));
    }

    // ── Non-existent account ──────────────────────────────────────────────

    @Test
    @DisplayName("non-existent account returns 404 for user caller")
    void nonexistent_account_user_caller_gets_404() {
        when(repo.findById(ACCOUNT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                service.getBalance(ACCOUNT_ID, CallerContext.user(USER_ID)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(NOT_FOUND));
    }

    @Test
    @DisplayName("non-existent account returns 404 for internal caller")
    void nonexistent_account_internal_caller_gets_404() {
        when(repo.findById(ACCOUNT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                service.getBalance(ACCOUNT_ID, CallerContext.internal()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(NOT_FOUND));
    }

    // ── No balance service call on denied requests ─────────────────────────

    @Test
    @DisplayName("balance not computed when ownership check fails — no unnecessary DB query")
    void no_balance_query_on_denied_request() {
        when(repo.findById(ACCOUNT_ID)).thenReturn(Optional.of(wallet(OTHER_USER)));

        try {
            service.getBalance(ACCOUNT_ID, CallerContext.user(USER_ID));
        } catch (ResponseStatusException ignored) {}

        verifyNoInteractions(balanceService);
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private static LedgerAccountEntity wallet(UUID ownerId) {
        return new LedgerAccountEntity("USER_WALLET", "USER", ownerId, "wallet",
                Instant.now(FIXED_CLOCK));
    }

    private static LedgerAccountEntity vaultAccount() {
        return new LedgerAccountEntity("VAULT", "VAULT", UUID.randomUUID(), "vault",
                Instant.now(FIXED_CLOCK));
    }

    private static LedgerAccountEntity systemAccount() {
        LedgerAccountEntity acc = new LedgerAccountEntity(
                "PAYSTACK_SETTLEMENT", "SYSTEM", null, "settlement",
                Instant.now(FIXED_CLOCK));
        return acc;
    }
}
