package com.stash.payments.ledger.service;

import com.stash.payments.ledger.api.StatementCursor;
import com.stash.payments.ledger.api.dto.StatementResponse;
import com.stash.payments.ledger.domain.LedgerAccountEntity;
import com.stash.payments.ledger.repository.LedgerAccountRepository;
import com.stash.payments.ledger.repository.LedgerEntryRepository;
import com.stash.payments.shared.security.CallerContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.http.HttpStatus.*;

class StatementServiceTest {

    private final LedgerAccountRepository accountRepo    = Mockito.mock(LedgerAccountRepository.class);
    private final LedgerEntryRepository   entryRepo      = Mockito.mock(LedgerEntryRepository.class);
    private final BalanceService          balanceService = Mockito.mock(BalanceService.class);
    private final StatementService        service =
            new StatementService(accountRepo, entryRepo, balanceService);

    private static final UUID ACCOUNT_ID = UUID.randomUUID();
    private static final UUID USER_ID    = UUID.randomUUID();
    private static final UUID OTHER_USER = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(accountRepo.findById(ACCOUNT_ID))
                .thenReturn(Optional.of(wallet(USER_ID)));
        when(balanceService.computeBalanceFast(ACCOUNT_ID)).thenReturn(50_000L);
        when(entryRepo.sumSignedAmountsAfter(eq(ACCOUNT_ID), any())).thenReturn(0L);
    }

    // ── Happy path ────────────────────────────────────────────────────────

    @Test
    @DisplayName("returns entries newest-first with correct directions")
    void returns_entries_newest_first() {
        when(entryRepo.findStatementPage(any(), any(), any(), any(), any(), anyInt()))
                .thenReturn(twoEntries());

        var result = service.getStatement(ACCOUNT_ID, null, null, null, null,
                CallerContext.user(USER_ID));

        assertThat(result.entries()).hasSize(2);
        assertThat(result.entries().get(0).direction()).isEqualTo("CREDIT");  // newest first
        assertThat(result.entries().get(1).direction()).isEqualTo("DEBIT");
    }

    @Test
    @DisplayName("empty statement: no entries, no cursor")
    void empty_statement() {
        when(entryRepo.findStatementPage(any(), any(), any(), any(), any(), anyInt()))
                .thenReturn(List.of());

        var result = service.getStatement(ACCOUNT_ID, null, null, null, null,
                CallerContext.user(USER_ID));

        assertThat(result.entries()).isEmpty();
        assertThat(result.nextCursor()).isNull();
        assertThat(result.hasMore()).isFalse();
    }

    // ── Running balance ───────────────────────────────────────────────────

    @Test
    @DisplayName("running balance computed correctly for a single CREDIT entry")
    void running_balance_single_credit() {
        when(balanceService.computeBalanceFast(ACCOUNT_ID)).thenReturn(10_000L);
        when(entryRepo.sumSignedAmountsAfter(eq(ACCOUNT_ID), any())).thenReturn(0L);
        List<Object[]> single = new ArrayList<>();
        single.add(entry(UUID.randomUUID(), "CREDIT", 10_000L,
                Instant.parse("2026-06-24T09:00:00Z")));
        when(entryRepo.findStatementPage(any(), any(), any(), any(), any(), anyInt()))
                .thenReturn(single);

        var result = service.getStatement(ACCOUNT_ID, null, null, null, null,
                CallerContext.user(USER_ID));

        assertThat(result.entries().get(0).runningBalancePesewas()).isEqualTo(10_000L);
    }

    @Test
    @DisplayName("running balance: two entries — oldest shows lower, newest shows higher")
    void running_balance_two_entries() {
        // Total = 30000p. Oldest CREDIT 10000, newest CREDIT 20000.
        // sumSignedAmountsAfter(olderTs) = 20000 (the newer credit).
        // anchorBalance = 30000 - 20000 = 10000 (balance AFTER older entry).
        Instant olderTs = Instant.parse("2026-06-24T08:00:00Z");
        Instant newerTs = Instant.parse("2026-06-24T09:00:00Z");
        UUID    olderId = UUID.randomUUID();
        UUID    newerId = UUID.randomUUID();

        when(balanceService.computeBalanceFast(ACCOUNT_ID)).thenReturn(30_000L);
        when(entryRepo.sumSignedAmountsAfter(eq(ACCOUNT_ID), eq(olderTs))).thenReturn(20_000L);
        List<Object[]> twoRows = new ArrayList<>();
        twoRows.add(entry(newerId, "CREDIT", 20_000L, newerTs));   // row 0 = newest
        twoRows.add(entry(olderId, "CREDIT", 10_000L, olderTs));   // row 1 = oldest
        when(entryRepo.findStatementPage(any(), any(), any(), any(), any(), anyInt()))
                .thenReturn(twoRows);

        var result = service.getStatement(ACCOUNT_ID, null, null, null, null,
                CallerContext.user(USER_ID));

        assertThat(result.entries().get(1).runningBalancePesewas()).isEqualTo(10_000L);  // older
        assertThat(result.entries().get(0).runningBalancePesewas()).isEqualTo(30_000L);  // newer
    }

    // ── Cursor pagination ─────────────────────────────────────────────────

    @Test
    @DisplayName("hasMore = true when fetch returns more than limit; nextCursor set")
    void has_more_when_extra_row_returned() {
        when(entryRepo.findStatementPage(any(), any(), any(), any(), any(), anyInt()))
                .thenReturn(threeEntries());

        var result = service.getStatement(ACCOUNT_ID, null, 2, null, null,
                CallerContext.user(USER_ID));

        assertThat(result.entries()).hasSize(2);
        assertThat(result.hasMore()).isTrue();
        assertThat(result.nextCursor()).isNotNull();
    }

    @Test
    @DisplayName("nextCursor decodes to the last visible entry's created_at and id")
    void next_cursor_decodes_correctly() {
        // With limit=1, the visible page has 1 entry (the newest).
        // The cursor points to the oldest visible entry = the only visible entry.
        Instant olderTs = Instant.parse("2026-06-24T08:00:00Z");
        Instant newerTs = Instant.parse("2026-06-24T09:00:00Z");
        UUID    newerId = UUID.randomUUID();

        List<Object[]> cursorRows = new ArrayList<>();
        cursorRows.add(entry(newerId, "CREDIT", 10_000L, newerTs));                // visible
        cursorRows.add(entry(UUID.randomUUID(), "CREDIT", 5_000L, olderTs));       // truncated
        when(entryRepo.findStatementPage(any(), any(), any(), any(), any(), anyInt()))
                .thenReturn(cursorRows);

        var result = service.getStatement(ACCOUNT_ID, null, 1, null, null,
                CallerContext.user(USER_ID));

        assertThat(result.nextCursor()).isNotNull();
        StatementCursor decoded = StatementCursor.decode(result.nextCursor());
        // Cursor should point to the oldest visible entry (= the only one shown)
        assertThat(decoded.entryId()).isEqualTo(newerId);
        assertThat(decoded.createdAt()).isEqualTo(newerTs);
    }

    @Test
    @DisplayName("limit capped at 50 even when larger limit requested")
    void limit_capped_at_50() {
        when(entryRepo.findStatementPage(any(), any(), any(), any(), any(), anyInt()))
                .thenReturn(List.of());

        service.getStatement(ACCOUNT_ID, null, 200, null, null, CallerContext.user(USER_ID));

        verify(entryRepo).findStatementPage(any(), any(), any(), any(), any(), eq(51));
    }

    @Test
    @DisplayName("invalid cursor returns 400")
    void invalid_cursor_returns_400() {
        assertThatThrownBy(() ->
                service.getStatement(ACCOUNT_ID, "not-valid-base64!!!",
                        null, null, null, CallerContext.user(USER_ID)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(BAD_REQUEST));
    }

    // ── Ownership enforcement ─────────────────────────────────────────────

    @Test
    @DisplayName("user querying another user's account returns 404")
    void wrong_user_gets_404() {
        assertThatThrownBy(() ->
                service.getStatement(ACCOUNT_ID, null, null, null, null,
                        CallerContext.user(OTHER_USER)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(NOT_FOUND));
    }

    @Test
    @DisplayName("non-existent account returns 404")
    void nonexistent_account_returns_404() {
        when(accountRepo.findById(ACCOUNT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                service.getStatement(ACCOUNT_ID, null, null, null, null,
                        CallerContext.user(USER_ID)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(NOT_FOUND));
    }

    @Test
    @DisplayName("internal caller may query any account")
    void internal_caller_bypasses_ownership() {
        when(accountRepo.findById(ACCOUNT_ID)).thenReturn(Optional.of(wallet(OTHER_USER)));
        when(entryRepo.findStatementPage(any(), any(), any(), any(), any(), anyInt()))
                .thenReturn(List.of());

        assertThatCode(() ->
                service.getStatement(ACCOUNT_ID, null, null, null, null,
                        CallerContext.internal()))
                .doesNotThrowAnyException();
    }

    // ── Date range filtering ──────────────────────────────────────────────

    @Test
    @DisplayName("date range passed to repository query")
    void date_range_passed_to_repository() {
        Instant from = Instant.parse("2026-06-01T00:00:00Z");
        Instant to   = Instant.parse("2026-06-30T23:59:59Z");
        when(entryRepo.findStatementPage(any(), any(), any(), any(), any(), anyInt()))
                .thenReturn(List.of());

        service.getStatement(ACCOUNT_ID, null, null, from, to, CallerContext.user(USER_ID));

        verify(entryRepo).findStatementPage(
                eq(ACCOUNT_ID), eq(from), eq(to), isNull(), isNull(), anyInt());
    }

    // ── Cedis formatting ──────────────────────────────────────────────────

    @Test
    @DisplayName("amount_cedis formatted correctly for 10050 pesewas → 100.50")
    void cedis_formatting() {
        List<Object[]> cedisRow = new ArrayList<>();
        cedisRow.add(entry(UUID.randomUUID(), "CREDIT", 10_050L,
                Instant.parse("2026-06-24T09:00:00Z")));
        when(entryRepo.findStatementPage(any(), any(), any(), any(), any(), anyInt()))
                .thenReturn(cedisRow);

        var result = service.getStatement(ACCOUNT_ID, null, null, null, null,
                CallerContext.user(USER_ID));

        assertThat(result.entries().get(0).amountCedis()).isEqualTo("100.50");
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private static LedgerAccountEntity wallet(UUID ownerId) {
        return new LedgerAccountEntity("USER_WALLET", "USER", ownerId, "wallet",
                Instant.parse("2026-01-01T00:00:00Z"));
    }

    private static Object[] entry(UUID id, String direction, long amount, Instant createdAt) {
        return new Object[]{
                id,
                direction,
                amount,
                "Test entry narrative",
                Timestamp.from(createdAt),
                "STSH-202606-TEST01",
                "DEPOSIT"
        };
    }

    private static List<Object[]> twoEntries() {
        List<Object[]> rows = new ArrayList<>();
        rows.add(entry(UUID.randomUUID(), "CREDIT", 20_000L, Instant.parse("2026-06-24T09:00:00Z")));
        rows.add(entry(UUID.randomUUID(), "DEBIT",  10_000L, Instant.parse("2026-06-24T08:00:00Z")));
        return rows;
    }

    private static List<Object[]> threeEntries() {
        List<Object[]> rows = new ArrayList<>();
        rows.add(entry(UUID.randomUUID(), "CREDIT", 30_000L, Instant.parse("2026-06-24T10:00:00Z")));
        rows.add(entry(UUID.randomUUID(), "CREDIT", 20_000L, Instant.parse("2026-06-24T09:00:00Z")));
        rows.add(entry(UUID.randomUUID(), "CREDIT", 10_000L, Instant.parse("2026-06-24T08:00:00Z")));
        return rows;
    }
}
