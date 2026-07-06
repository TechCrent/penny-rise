package com.stash.platform.transaction.service;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.stash.platform.transaction.api.dto.UnifiedTransactionItem;

import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Generates a downloadable statement (CSV or PDF) of a user's full
 * transaction history — gap-analysis fix: history was previously view-only
 * in-app, with no export of any kind.
 *
 * <p>Reuses {@link TransactionHistoryService#list} looped across its
 * cursor pagination rather than a new query — the same data already backs
 * the in-app history screen.
 */
@Service
public class TransactionStatementExportService {

    private static final int PAGE_SIZE = 50;
    // Safety cap so a pathological account (or a bug in cursor advancement)
    // can't turn this into an unbounded loop / unbounded response body.
    private static final int MAX_ROWS = 5000;

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneOffset.UTC);

    private final TransactionHistoryService historyService;

    public TransactionStatementExportService(TransactionHistoryService historyService) {
        this.historyService = historyService;
    }

    public byte[] exportCsv(UUID userId) {
        List<UnifiedTransactionItem> items = fetchAll(userId);

        StringBuilder csv = new StringBuilder();
        csv.append("Reference,Type,Account,Direction,Amount (GHS),Narrative,Counterparty,Status,Date\n");
        for (UnifiedTransactionItem item : items) {
            csv.append(csvField(item.transactionReference())).append(',')
               .append(csvField(item.transactionType())).append(',')
               .append(csvField(item.accountName())).append(',')
               .append(csvField(item.direction())).append(',')
               .append(csvField(item.amountCedis())).append(',')
               .append(csvField(item.narrative())).append(',')
               .append(csvField(item.counterpartyName())).append(',')
               .append(csvField(item.status())).append(',')
               .append(csvField(DATE_FORMAT.format(item.createdAt())))
               .append('\n');
        }
        return csv.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    public byte[] exportPdf(UUID userId) {
        List<UnifiedTransactionItem> items = fetchAll(userId);

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4.rotate(), 24, 24, 24, 24);
            PdfWriter.getInstance(document, out);
            document.open();

            document.add(new Paragraph("Stash Transaction Statement",
                    new Font(Font.HELVETICA, 16, Font.BOLD)));
            document.add(new Paragraph(" "));

            PdfPTable table = new PdfPTable(9);
            table.setWidthPercentage(100);
            for (String header : List.of("Reference", "Type", "Account", "Direction", "Amount (GHS)",
                    "Narrative", "Counterparty", "Status", "Date")) {
                PdfPCell cell = new PdfPCell(new Paragraph(header, new Font(Font.HELVETICA, 9, Font.BOLD)));
                cell.setHorizontalAlignment(Element.ALIGN_LEFT);
                table.addCell(cell);
            }

            Font rowFont = new Font(Font.HELVETICA, 8);
            for (UnifiedTransactionItem item : items) {
                table.addCell(new Paragraph(item.transactionReference(), rowFont));
                table.addCell(new Paragraph(item.transactionType(), rowFont));
                table.addCell(new Paragraph(item.accountName(), rowFont));
                table.addCell(new Paragraph(item.direction(), rowFont));
                table.addCell(new Paragraph(item.amountCedis(), rowFont));
                table.addCell(new Paragraph(item.narrative() == null ? "" : item.narrative(), rowFont));
                table.addCell(new Paragraph(
                        item.counterpartyName() == null ? "" : item.counterpartyName(), rowFont));
                table.addCell(new Paragraph(item.status(), rowFont));
                table.addCell(new Paragraph(DATE_FORMAT.format(item.createdAt()), rowFont));
            }

            document.add(table);
            document.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to generate PDF statement", e);
        }
    }

    /** Fetches the user's full transaction history, looped across pages, up to {@link #MAX_ROWS}. */
    public List<UnifiedTransactionItem> fetchAll(UUID userId) {
        List<UnifiedTransactionItem> all = new ArrayList<>();
        String cursor = null;
        boolean hasMore = true;

        while (hasMore && all.size() < MAX_ROWS) {
            var page = historyService.list(userId, null, null, null, cursor, PAGE_SIZE);
            all.addAll(page.transactions());
            hasMore = page.hasMore();
            cursor = page.nextCursor();
            if (cursor == null) {
                break;
            }
        }
        return all;
    }

    private static String csvField(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
