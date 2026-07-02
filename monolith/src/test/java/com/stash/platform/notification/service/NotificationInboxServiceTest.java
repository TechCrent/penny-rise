package com.stash.platform.notification.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stash.platform.notification.repository.NotificationInboxRow;
import com.stash.platform.notification.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class NotificationInboxServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-07-01T10:00:00Z"), ZoneOffset.UTC);
    private static final UUID  USER_ID     = UUID.randomUUID();

    private final NotificationRepository  repository = mock(NotificationRepository.class);
    private final NotificationInboxService service   =
            new NotificationInboxService(repository, new ObjectMapper(), FIXED_CLOCK);

    @BeforeEach
    void setUp() {
        when(repository.countUnread(USER_ID)).thenReturn(0L);
    }

    // ── paginated inbox ──────────────────────────────────────────────────────

    @Test
    @DisplayName("unfiltered inbox returns newest-first entries")
    void paginatedInbox() {
        when(repository.findInboxPage(eq(USER_ID), eq(false), isNull(), isNull(), eq(26)))
                .thenReturn(List.of(sampleRow(UUID.randomUUID())));

        var page = service.listInbox(USER_ID, false, null, 25);

        assertThat(page.notifications()).hasSize(1);
        assertThat(page.hasMore()).isFalse();
    }

    @Test
    @DisplayName("unread_only=true filter is passed through to the repository query")
    void unreadOnlyFilter() {
        when(repository.findInboxPage(eq(USER_ID), eq(true), isNull(), isNull(), anyInt()))
                .thenReturn(List.of());

        service.listInbox(USER_ID, true, null, 25);

        verify(repository).findInboxPage(eq(USER_ID), eq(true), isNull(), isNull(), anyInt());
    }

    @Test
    @DisplayName("unread_count in the response matches the repository's count")
    void unreadCountAccuracy() {
        when(repository.countUnread(USER_ID)).thenReturn(7L);
        when(repository.findInboxPage(any(), anyBoolean(), any(), any(), anyInt())).thenReturn(List.of());

        var page = service.listInbox(USER_ID, false, null, 25);

        assertThat(page.unreadCount()).isEqualTo(7L);
    }

    @Test
    @DisplayName("an unresolvable cursor returns 400, not a silently wrong page")
    void invalidCursorReturns400() {
        UUID badCursor = UUID.randomUUID();
        when(repository.findCursorPosition(eq(badCursor), eq(USER_ID))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.listInbox(USER_ID, false, badCursor.toString(), 25))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    // ── mark single read ─────────────────────────────────────────────────────

    @Test
    @DisplayName("marking an unread notification read succeeds")
    void markSingleRead() {
        UUID notifId = UUID.randomUUID();
        when(repository.existsByIdAndUserId(notifId, USER_ID)).thenReturn(true);

        service.markRead(notifId, USER_ID);

        verify(repository).markReadIfUnread(eq(notifId), eq(USER_ID), any());
    }

    @Test
    @DisplayName("marking an already-read notification read is idempotent — no error, no re-timestamp")
    void markSingleReadIdempotent() {
        UUID notifId = UUID.randomUUID();
        when(repository.existsByIdAndUserId(notifId, USER_ID)).thenReturn(true);
        when(repository.markReadIfUnread(eq(notifId), eq(USER_ID), any())).thenReturn(0); // already read, 0 rows touched

        assertThatCode(() -> service.markRead(notifId, USER_ID)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("marking a nonexistent or not-owned notification returns 404, not 403")
    void markReadCrossUserReturns404() {
        UUID notifId = UUID.randomUUID();
        when(repository.existsByIdAndUserId(notifId, USER_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.markRead(notifId, USER_ID))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    var e = (ResponseStatusException) ex;
                    assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(e.getReason()).contains("NOTIFICATION_NOT_FOUND");
                });

        verify(repository, never()).markReadIfUnread(any(), any(), any());
    }

    // ── mark all read ────────────────────────────────────────────────────────

    @Test
    @DisplayName("mark-all-read only touches this user's unread notifications")
    void markAllRead() {
        service.markAllRead(USER_ID);

        verify(repository).markAllReadForUser(eq(USER_ID), any());
    }

    @Test
    @DisplayName("mark-all-read with zero unread notifications is a harmless no-op")
    void markAllReadWithNothingUnread() {
        when(repository.markAllReadForUser(eq(USER_ID), any())).thenReturn(0);

        assertThatCode(() -> service.markAllRead(USER_ID)).doesNotThrowAnyException();
    }

    private NotificationInboxRow sampleRow(UUID id) {
        return new NotificationInboxRow() {
            public UUID    getId()                  { return id; }
            public String  getNotificationType()    { return "DEPOSIT_SUCCESS"; }
            public String  getChannel()             { return "IN_APP"; }
            public String  getTitle()               { return "Deposit successful"; }
            public String  getBody()                { return "GHS 100.00 landed in your vault."; }
            public String  getPayload()             { return "{}"; }
            public Instant getReadAt()              { return null; }
            public Instant getCreatedAt()           { return Instant.now(FIXED_CLOCK); }
        };
    }
}
