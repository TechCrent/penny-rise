package com.stash.platform.transfer.api;

import com.stash.platform.transfer.repository.MonthlyTransferQuotaRepository;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users/me")
public class TransferQuotaController {

    static final int FREE_QUOTA_LIMIT = 5;

    private final MonthlyTransferQuotaRepository quotaRepo;

    public TransferQuotaController(MonthlyTransferQuotaRepository quotaRepo) {
        this.quotaRepo = quotaRepo;
    }

    @GetMapping("/transfer-quota")
    public QuotaResponse getQuota(@AuthenticationPrincipal UUID callerId) {
        LocalDate today = LocalDate.now();
        int used = quotaRepo
                .findByUserAndMonthForUpdate(callerId, today.getYear(), today.getMonthValue())
                .map(q -> q.getFreeTransfersUsed())
                .orElse(0);

        int     remaining    = Math.max(0, FREE_QUOTA_LIMIT - used);
        boolean nextIsFree   = remaining > 0;
        long    feePesewas   = nextIsFree ? 0L : 200L;

        return new QuotaResponse(used, remaining, FREE_QUOTA_LIMIT,
                nextIsFree, feePesewas, nextIsFree ? "0.00" : "2.00");
    }

    public record QuotaResponse(
            int     freeTransfersUsed,
            int     freeTransfersRemaining,
            int     freeQuotaLimit,
            boolean nextTransferIsFree,
            long    feeIfTransferNowPesewas,
            String  feeIfTransferNowCedis
    ) {}
}
