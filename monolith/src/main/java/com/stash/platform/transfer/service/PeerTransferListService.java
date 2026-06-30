package com.stash.platform.transfer.service;

import com.stash.platform.transfer.api.dto.TransferListItemResponse;
import com.stash.platform.transfer.api.dto.TransferListResponse;
import com.stash.platform.transfer.domain.PeerTransferEntity;
import com.stash.platform.transfer.repository.PeerTransferRepository;
import com.stash.platform.user.domain.User;
import com.stash.platform.user.repository.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Returns a paginated, optionally filtered transfer history for the caller.
 *
 * Direction: "sent" (caller is sender), "received" (caller is recipient),
 * or "all" (default) — both, merged by created_at DESC via UNION ALL.
 *
 * Cursor: composite (created_at, id) encoded as base64. Pass the previous
 * response's next_cursor to get the next page.
 *
 * Counterparty display names are batch-resolved in one findAllById call.
 * Falls back to email prefix if displayName is null or blank.
 */
@Service
@Transactional(readOnly = true)
public class PeerTransferListService {

    static final int MAX_LIMIT     = 50;
    static final int DEFAULT_LIMIT = 20;

    private final PeerTransferRepository transferRepo;
    private final UserRepository         userRepo;

    public PeerTransferListService(PeerTransferRepository transferRepo,
                                    UserRepository userRepo) {
        this.transferRepo = transferRepo;
        this.userRepo     = userRepo;
    }

    public TransferListResponse list(UUID callerId, String direction,
                                      Instant fromDate, Instant toDate,
                                      String cursor, Integer limit) {
        int pageSize  = resolveLimit(limit);
        int fetchSize = pageSize + 1;

        TransferCursor.DecodedCursor decoded = TransferCursor.parse(cursor);
        Instant cursorTime = decoded != null ? decoded.time() : null;
        UUID    cursorId   = decoded != null ? decoded.id()   : null;

        String dir = direction != null ? direction.toLowerCase() : "all";

        List<PeerTransferEntity> rows = switch (dir) {
            case "sent" -> transferRepo.findSent(
                    callerId, fromDate, toDate, cursorTime, cursorId,
                    PageRequest.of(0, fetchSize));
            case "received" -> transferRepo.findReceived(
                    callerId, fromDate, toDate, cursorTime, cursorId,
                    PageRequest.of(0, fetchSize));
            default -> transferRepo.findAllByUser(
                    callerId, fromDate, toDate, cursorTime, cursorId, fetchSize);
        };

        boolean hasMore = rows.size() > pageSize;
        List<PeerTransferEntity> page = hasMore ? rows.subList(0, pageSize) : rows;

        if (page.isEmpty()) {
            return TransferListResponse.of(List.of(), null);
        }

        Set<UUID> counterpartyIds = page.stream()
                .map(t -> callerId.equals(t.getSenderUserId())
                        ? t.getRecipientUserId()
                        : t.getSenderUserId())
                .collect(Collectors.toSet());

        Map<UUID, String> displayNames = resolveDisplayNames(counterpartyIds);

        List<TransferListItemResponse> items = page.stream()
                .map(t -> {
                    boolean isSent        = callerId.equals(t.getSenderUserId());
                    UUID    counterpartyId = isSent ? t.getRecipientUserId() : t.getSenderUserId();
                    return TransferListItemResponse.of(
                            t, callerId,
                            displayNames.getOrDefault(counterpartyId, "Unknown"));
                })
                .toList();

        PeerTransferEntity last = page.get(page.size() - 1);
        String nextCursor = hasMore
                ? TransferCursor.encode(last.getCreatedAt(), last.getId())
                : null;

        return TransferListResponse.of(items, nextCursor);
    }

    private int resolveLimit(Integer requested) {
        if (requested == null || requested <= 0) return DEFAULT_LIMIT;
        return Math.min(requested, MAX_LIMIT);
    }

    private Map<UUID, String> resolveDisplayNames(Set<UUID> userIds) {
        if (userIds.isEmpty()) return Map.of();
        return userRepo.findAllById(userIds).stream()
                .collect(Collectors.toMap(
                        User::getId,
                        u -> {
                            String name = u.getDisplayName();
                            return (name != null && !name.isBlank())
                                    ? name
                                    : u.getEmail().split("@")[0];
                        }
                ));
    }
}
