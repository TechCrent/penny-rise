package com.stash.platform.transfer.service;

import com.stash.platform.transfer.api.dto.TransferListItemResponse;
import com.stash.platform.transfer.api.dto.TransferListResponse;
import com.stash.platform.transfer.domain.PeerTransferEntity;
import com.stash.platform.transfer.repository.PeerTransferRepository;
import com.stash.platform.user.domain.User;
import com.stash.platform.user.repository.UserRepository;
import org.junit.jupiter.api.*;
import org.mockito.*;
import org.springframework.data.domain.Pageable;

import java.time.*;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PeerTransferListServiceTest {

    private final PeerTransferRepository transferRepo = Mockito.mock(PeerTransferRepository.class);
    private final UserRepository         userRepo     = Mockito.mock(UserRepository.class);

    private final PeerTransferListService service =
            new PeerTransferListService(transferRepo, userRepo);

    private static final UUID CALLER_ID = UUID.randomUUID();
    private static final UUID OTHER_ID  = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(transferRepo.findSent(any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(List.of());
        when(transferRepo.findReceived(any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(List.of());
        when(transferRepo.findAllByUser(any(), any(), any(), any(), any(), anyInt()))
                .thenReturn(List.of());
        when(userRepo.findAllById(any())).thenReturn(List.of());
    }

    // ── Empty list ────────────────────────────────────────────────────────

    @Test
    @DisplayName("no transfers: returns empty list with null cursor")
    void empty_list() {
        TransferListResponse result = service.list(CALLER_ID, "all", null, null, null, null);

        assertThat(result.transfers()).isEmpty();
        assertThat(result.nextCursor()).isNull();
        assertThat(result.hasMore()).isFalse();
        assertThat(result.count()).isEqualTo(0);
    }

    // ── Sent transfers ────────────────────────────────────────────────────

    @Test
    @DisplayName("direction=sent: only findSent called, not findReceived or findAllByUser")
    void direction_sent_uses_correct_query() {
        service.list(CALLER_ID, "sent", null, null, null, null);
        verify(transferRepo).findSent(eq(CALLER_ID), any(), any(), any(), any(), any(Pageable.class));
        verify(transferRepo, never()).findReceived(any(), any(), any(), any(), any(), any(Pageable.class));
        verify(transferRepo, never()).findAllByUser(any(), any(), any(), any(), any(), anyInt());
    }

    @Test
    @DisplayName("sent transfers: direction field = SENT, counterparty = recipient")
    void sent_transfers_have_correct_direction_and_counterparty() {
        when(transferRepo.findSent(any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(List.of(sentTransfer()));
        when(userRepo.findAllById(any()))
                .thenReturn(List.of(user(OTHER_ID, "Kwame Asante")));

        TransferListResponse result = service.list(CALLER_ID, "sent", null, null, null, null);

        assertThat(result.transfers()).hasSize(1);
        TransferListItemResponse item = result.transfers().get(0);
        assertThat(item.direction()).isEqualTo("SENT");
        assertThat(item.counterpartyUserId()).isEqualTo(OTHER_ID);
        assertThat(item.counterpartyDisplayName()).isEqualTo("Kwame Asante");
    }

    // ── Received transfers ────────────────────────────────────────────────

    @Test
    @DisplayName("direction=received: only findReceived called")
    void direction_received_uses_correct_query() {
        service.list(CALLER_ID, "received", null, null, null, null);
        verify(transferRepo).findReceived(eq(CALLER_ID), any(), any(), any(), any(), any(Pageable.class));
        verify(transferRepo, never()).findSent(any(), any(), any(), any(), any(), any(Pageable.class));
    }

    @Test
    @DisplayName("received transfers: direction field = RECEIVED, counterparty = sender")
    void received_transfers_have_correct_direction_and_counterparty() {
        when(transferRepo.findReceived(any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(List.of(receivedTransfer()));
        when(userRepo.findAllById(any()))
                .thenReturn(List.of(user(OTHER_ID, "Akua Mensah")));

        TransferListResponse result = service.list(CALLER_ID, "received", null, null, null, null);

        assertThat(result.transfers()).hasSize(1);
        TransferListItemResponse item = result.transfers().get(0);
        assertThat(item.direction()).isEqualTo("RECEIVED");
        assertThat(item.counterpartyUserId()).isEqualTo(OTHER_ID);
        assertThat(item.counterpartyDisplayName()).isEqualTo("Akua Mensah");
    }

    // ── All transfers ─────────────────────────────────────────────────────

    @Test
    @DisplayName("direction=all: findAllByUser called with UNION ALL query")
    void direction_all_uses_union_query() {
        service.list(CALLER_ID, "all", null, null, null, null);
        verify(transferRepo).findAllByUser(eq(CALLER_ID), any(), any(), any(), any(), anyInt());
        verify(transferRepo, never()).findSent(any(), any(), any(), any(), any(), any(Pageable.class));
    }

    @Test
    @DisplayName("null direction defaults to all")
    void null_direction_defaults_to_all() {
        service.list(CALLER_ID, null, null, null, null, null);
        verify(transferRepo).findAllByUser(eq(CALLER_ID), any(), any(), any(), any(), anyInt());
    }

    @Test
    @DisplayName("all: returns sent and received transfers mixed; direction per row is correct")
    void all_mixes_sent_and_received() {
        when(transferRepo.findAllByUser(any(), any(), any(), any(), any(), anyInt()))
                .thenReturn(List.of(sentTransfer(), receivedTransfer()));
        when(userRepo.findAllById(any()))
                .thenReturn(List.of(user(OTHER_ID, "Kwame Asante")));

        TransferListResponse result = service.list(CALLER_ID, "all", null, null, null, null);

        assertThat(result.transfers()).hasSize(2);
        assertThat(result.transfers().get(0).direction()).isEqualTo("SENT");
        assertThat(result.transfers().get(1).direction()).isEqualTo("RECEIVED");
    }

    // ── Display name fallback ─────────────────────────────────────────────

    @Test
    @DisplayName("counterparty with null displayName: falls back to email prefix")
    void display_name_falls_back_to_email_prefix() {
        when(transferRepo.findSent(any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(List.of(sentTransfer()));
        when(userRepo.findAllById(any()))
                .thenReturn(List.of(userNoName(OTHER_ID, "kwame@stash.test")));

        TransferListResponse result = service.list(CALLER_ID, "sent", null, null, null, null);

        assertThat(result.transfers().get(0).counterpartyDisplayName()).isEqualTo("kwame");
    }

    @Test
    @DisplayName("counterparty not found in user table: falls back to 'Unknown'")
    void unknown_counterparty_display_name() {
        when(transferRepo.findSent(any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(List.of(sentTransfer()));
        when(userRepo.findAllById(any())).thenReturn(List.of());

        TransferListResponse result = service.list(CALLER_ID, "sent", null, null, null, null);

        assertThat(result.transfers().get(0).counterpartyDisplayName()).isEqualTo("Unknown");
    }

    // ── Pagination ────────────────────────────────────────────────────────

    @Test
    @DisplayName("page of 2 with limit=2: hasMore=true, next_cursor set")
    void pagination_has_more_sets_cursor() {
        when(transferRepo.findSent(any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(List.of(
                        sentTransferAt(Instant.parse("2026-06-24T10:00:00Z")),
                        sentTransferAt(Instant.parse("2026-06-24T09:00:00Z")),
                        sentTransferAt(Instant.parse("2026-06-24T08:00:00Z"))
                ));

        TransferListResponse result = service.list(CALLER_ID, "sent", null, null, null, 2);

        assertThat(result.transfers()).hasSize(2);
        assertThat(result.hasMore()).isTrue();
        assertThat(result.nextCursor()).isNotNull();
    }

    @Test
    @DisplayName("page of 2 with limit=2 and only 2 items: hasMore=false, no cursor")
    void pagination_exact_page_no_cursor() {
        when(transferRepo.findSent(any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(List.of(
                        sentTransferAt(Instant.parse("2026-06-24T10:00:00Z")),
                        sentTransferAt(Instant.parse("2026-06-24T09:00:00Z"))
                ));

        TransferListResponse result = service.list(CALLER_ID, "sent", null, null, null, 2);

        assertThat(result.transfers()).hasSize(2);
        assertThat(result.hasMore()).isFalse();
        assertThat(result.nextCursor()).isNull();
    }

    @Test
    @DisplayName("limit > 50 capped at 50")
    void limit_capped_at_max() {
        service.list(CALLER_ID, "sent", null, null, null, 100);

        verify(transferRepo).findSent(any(), any(), any(), any(), any(),
                argThat(p -> p.getPageSize() == 51));
    }

    @Test
    @DisplayName("limit = null defaults to 20")
    void limit_defaults_to_20() {
        service.list(CALLER_ID, "sent", null, null, null, null);

        verify(transferRepo).findSent(any(), any(), any(), any(), any(),
                argThat(p -> p.getPageSize() == 21));
    }

    // ── Date filters forwarded to repository ──────────────────────────────

    @Test
    @DisplayName("fromDate and toDate are forwarded to findSent query")
    void date_filters_forwarded() {
        Instant from = Instant.parse("2026-06-01T00:00:00Z");
        Instant to   = Instant.parse("2026-06-30T23:59:59Z");

        service.list(CALLER_ID, "sent", from, to, null, null);

        verify(transferRepo).findSent(eq(CALLER_ID), eq(from), eq(to), any(), any(), any(Pageable.class));
    }

    // ── Cursor encoding/decoding ───────────────────────────────────────────

    @Test
    @DisplayName("next_cursor from page 1 can be decoded and is non-null")
    void cursor_is_decodable() {
        when(transferRepo.findSent(any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(List.of(
                        sentTransferAt(Instant.parse("2026-06-24T10:00:00Z")),
                        sentTransferAt(Instant.parse("2026-06-24T09:00:00Z")),
                        sentTransferAt(Instant.parse("2026-06-24T08:00:00Z"))
                ));

        TransferListResponse page1 = service.list(CALLER_ID, "sent", null, null, null, 2);

        assertThat(page1.nextCursor()).isNotNull();
        TransferCursor.DecodedCursor decoded = TransferCursor.parse(page1.nextCursor());
        assertThat(decoded).isNotNull();
        assertThat(decoded.time()).isNotNull();
        assertThat(decoded.id()).isNotNull();
    }

    // ── Amount formatting ─────────────────────────────────────────────────

    @Test
    @DisplayName("amount and fee_amount formatted as cedis correctly")
    void cedis_formatting() {
        when(transferRepo.findSent(any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(List.of(sentTransferWithFee(50_000L, 200L)));
        when(userRepo.findAllById(any()))
                .thenReturn(List.of(user(OTHER_ID, "Test User")));

        TransferListResponse result = service.list(CALLER_ID, "sent", null, null, null, null);

        TransferListItemResponse item = result.transfers().get(0);
        assertThat(item.amountCedis()).isEqualTo("500.00");
        assertThat(item.feeAmountCedis()).isEqualTo("2.00");
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private PeerTransferEntity sentTransfer() {
        return sentTransferAt(Instant.parse("2026-06-24T10:00:00Z"));
    }

    private PeerTransferEntity sentTransferAt(Instant at) {
        PeerTransferEntity t = PeerTransferEntity.create(
                CALLER_ID, OTHER_ID, 50_000L, 0L, null,
                UUID.randomUUID().toString(), at);
        setField(t, "id", UUID.randomUUID());
        return t;
    }

    private PeerTransferEntity sentTransferWithFee(long amount, long fee) {
        PeerTransferEntity t = PeerTransferEntity.create(
                CALLER_ID, OTHER_ID, amount, fee, null,
                UUID.randomUUID().toString(), Instant.parse("2026-06-24T10:00:00Z"));
        setField(t, "id", UUID.randomUUID());
        return t;
    }

    private PeerTransferEntity receivedTransfer() {
        PeerTransferEntity t = PeerTransferEntity.create(
                OTHER_ID, CALLER_ID, 50_000L, 0L, null,
                UUID.randomUUID().toString(), Instant.parse("2026-06-24T09:00:00Z"));
        setField(t, "id", UUID.randomUUID());
        return t;
    }

    private static User user(UUID id, String displayName) {
        User u = new User();
        u.setId(id);
        u.setDisplayName(displayName);
        u.setEmail(displayName.toLowerCase().replace(" ", ".") + "@stash.test");
        return u;
    }

    private static User userNoName(UUID id, String email) {
        User u = new User();
        u.setId(id);
        u.setDisplayName(null);
        u.setEmail(email);
        return u;
    }

    private static void setField(Object obj, String name, Object value) {
        try {
            var f = findField(obj.getClass(), name);
            f.setAccessible(true);
            f.set(obj, value);
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    private static java.lang.reflect.Field findField(Class<?> c, String name)
            throws NoSuchFieldException {
        try { return c.getDeclaredField(name); }
        catch (NoSuchFieldException e) {
            if (c.getSuperclass() != null) return findField(c.getSuperclass(), name);
            throw e;
        }
    }
}
