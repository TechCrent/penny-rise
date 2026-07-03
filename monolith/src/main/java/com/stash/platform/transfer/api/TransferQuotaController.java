package com.stash.platform.transfer.api;

import com.stash.platform.subscription.policy.SubscriptionPolicy;
import com.stash.platform.transfer.repository.MonthlyTransferQuotaRepository;
import com.stash.platform.user.domain.SubscriptionTier;
import com.stash.platform.user.domain.User;
import com.stash.platform.user.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users/me")
public class TransferQuotaController {

    private final MonthlyTransferQuotaRepository quotaRepo;
    private final UserRepository                 userRepo;
    private final SubscriptionPolicy             subscriptionPolicy;

    public TransferQuotaController(MonthlyTransferQuotaRepository quotaRepo,
                                    UserRepository userRepo,
                                    SubscriptionPolicy subscriptionPolicy) {
        this.quotaRepo = quotaRepo;
        this.userRepo = userRepo;
        this.subscriptionPolicy = subscriptionPolicy;
    }

    /**
     * v0.5-030: quota limit is tier-aware — was hardcoded to the FREE-tier
     * limit of 5, meaning a PREMIUM caller would have seen the wrong
     * number here (while PeerTransferService itself already applied the
     * correct, tier-aware limit at transfer time) — a real, pre-existing
     * inconsistency this fix closes.
     */
    @GetMapping("/transfer-quota")
    public QuotaResponse getQuota(@AuthenticationPrincipal UUID callerId) {
        User caller = userRepo.findById(callerId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.INTERNAL_SERVER_ERROR, "Authenticated user not found."));

        SubscriptionTier tier = caller.getSubscriptionTier();
        int quotaLimit = subscriptionPolicy.transfersPerMonth(tier);

        LocalDate today = LocalDate.now();
        int used = quotaRepo
                .findByUserAndMonth(callerId, today.getYear(), today.getMonthValue())
                .map(q -> q.getFreeTransfersUsed())
                .orElse(0);

        int     remaining    = Math.max(0, quotaLimit - used);
        boolean nextIsFree   = remaining > 0;
        long    feePesewas   = nextIsFree ? 0L : 200L;

        return new QuotaResponse(used, remaining, quotaLimit,
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
