package com.stash.payments.transaction.service;

import com.stash.payments.ledger.domain.LedgerAccountEntity;
import com.stash.payments.ledger.repository.LedgerAccountRepository;
import com.stash.payments.ledger.service.LedgerService;
import com.stash.payments.shared.security.CallerContext;
import com.stash.payments.transaction.api.dto.TransactionDetailResponse;
import com.stash.payments.transaction.api.dto.TransactionEntryDto;
import com.stash.payments.transaction.domain.TransactionEntity;
import com.stash.payments.transaction.repository.TransactionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class TransactionDetailService {

    private final TransactionRepository  txnRepo;
    private final LedgerService          ledgerService;
    private final LedgerAccountRepository accountRepo;

    public TransactionDetailService(TransactionRepository txnRepo,
                                     LedgerService ledgerService,
                                     LedgerAccountRepository accountRepo) {
        this.txnRepo      = txnRepo;
        this.ledgerService = ledgerService;
        this.accountRepo  = accountRepo;
    }

    @Transactional(readOnly = true)
    public TransactionDetailResponse getDetail(String reference, CallerContext caller) {
        TransactionEntity txn = txnRepo.findByReference(reference)
                .orElseThrow(() -> notFound(reference));
        return buildDetail(txn, caller);
    }

    /**
     * Same as {@link #getDetail} but keyed by the transaction's UUID primary
     * key instead of its string reference — needed because callers like
     * monolith's dispute-entity validator only have the UUID
     * (disputes.related_entity_id), not the reference.
     */
    @Transactional(readOnly = true)
    public TransactionDetailResponse getDetailById(UUID id, CallerContext caller) {
        TransactionEntity txn = txnRepo.findById(id)
                .orElseThrow(() -> notFound(id.toString()));
        return buildDetail(txn, caller);
    }

    private TransactionDetailResponse buildDetail(TransactionEntity txn, CallerContext caller) {
        List<Object[]> rawEntries = txn.getLedgerTransactionId() != null
                ? ledgerService.getEntriesForTransaction(txn.getLedgerTransactionId())
                : List.of();

        if (caller.isUser()) {
            assertCallerHasAccess(txn, rawEntries, caller);
        }

        List<TransactionEntryDto> entries = rawEntries.stream()
                .map(row -> TransactionEntryDto.of(
                        (String) row[1],
                        ((Number) row[2]).longValue(),
                        (String) row[3],
                        (UUID)   row[0],
                        (String) row[4]))
                .toList();

        return TransactionDetailResponse.of(txn, entries);
    }

    private void assertCallerHasAccess(TransactionEntity txn,
                                        List<Object[]> rawEntries,
                                        CallerContext caller) {
        UUID callerId = caller.userId();
        if (callerId == null) throw notFound(txn.getReference());
        if (callerId.equals(txn.getInitiatingUserId())) return;
        if (txn.getCounterpartyUserId() != null
                && callerId.equals(txn.getCounterpartyUserId())) return;
        if (callerOwnsAnyEntry(rawEntries, callerId)) return;
        throw notFound(txn.getReference());
    }

    private boolean callerOwnsAnyEntry(List<Object[]> entries, UUID callerId) {
        return entries.stream()
                .map(row -> (UUID) row[0])
                .flatMap(accountId -> accountRepo.findById(accountId).stream())
                .anyMatch(account -> callerId.equals(account.getOwnerId()));
    }

    private ResponseStatusException notFound(String reference) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Transaction not found: " + reference);
    }
}
