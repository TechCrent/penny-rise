package com.stash.platform.transfer.api;

import com.stash.platform.transfer.api.dto.TransferListResponse;
import com.stash.platform.transfer.service.PeerTransferListService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/transfers")
public class PeerTransferListController {

    private final PeerTransferListService listService;

    public PeerTransferListController(PeerTransferListService listService) {
        this.listService = listService;
    }

    @GetMapping
    public TransferListResponse listTransfers(
            @AuthenticationPrincipal UUID callerId,
            @RequestParam(value = "direction", required = false) String direction,
            @RequestParam(value = "from_date", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant fromDate,
            @RequestParam(value = "to_date", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant toDate,
            @RequestParam(value = "cursor", required = false) String cursor,
            @RequestParam(value = "limit",  required = false) Integer limit) {

        return listService.list(callerId, direction, fromDate, toDate, cursor, limit);
    }
}
