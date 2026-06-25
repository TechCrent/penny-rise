package com.stash.payments.transaction.api;

import com.stash.payments.shared.security.CallerContext;
import com.stash.payments.shared.security.CallerContextFilter;
import com.stash.payments.transaction.api.dto.TransactionDetailResponse;
import com.stash.payments.transaction.service.TransactionDetailService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionDetailController {

    private final TransactionDetailService transactionDetailService;

    public TransactionDetailController(TransactionDetailService transactionDetailService) {
        this.transactionDetailService = transactionDetailService;
    }

    @GetMapping("/{reference}")
    public TransactionDetailResponse getTransaction(
            @PathVariable String reference,
            @RequestAttribute(CallerContextFilter.ATTR) CallerContext callerContext) {

        return transactionDetailService.getDetail(reference, callerContext);
    }
}
