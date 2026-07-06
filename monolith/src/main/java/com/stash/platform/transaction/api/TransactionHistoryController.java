package com.stash.platform.transaction.api;

import com.stash.platform.transaction.api.dto.UnifiedTransactionHistoryResponse;
import com.stash.platform.transaction.service.TransactionHistoryService;
import com.stash.platform.transaction.service.TransactionStatementExportService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users/me/transactions")
public class TransactionHistoryController {

    private final TransactionHistoryService historyService;
    private final TransactionStatementExportService exportService;

    public TransactionHistoryController(TransactionHistoryService historyService,
                                        TransactionStatementExportService exportService) {
        this.historyService = historyService;
        this.exportService = exportService;
    }

    @GetMapping
    public UnifiedTransactionHistoryResponse list(
            @RequestParam(required = false) String transactionType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant toDate,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit,
            @AuthenticationPrincipal UUID userId) {
        return historyService.list(userId, transactionType, fromDate, toDate, cursor, limit);
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(
            @RequestParam(defaultValue = "csv") String format,
            @AuthenticationPrincipal UUID userId) {
        byte[] body;
        MediaType contentType;
        String filename;

        switch (format.toLowerCase()) {
            case "csv" -> {
                body = exportService.exportCsv(userId);
                contentType = MediaType.parseMediaType("text/csv");
                filename = "stash-statement.csv";
            }
            case "pdf" -> {
                body = exportService.exportPdf(userId);
                contentType = MediaType.APPLICATION_PDF;
                filename = "stash-statement.pdf";
            }
            default -> throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "format must be 'csv' or 'pdf'");
        }

        return ResponseEntity.ok()
                .contentType(contentType)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename).build().toString())
                .body(body);
    }
}
