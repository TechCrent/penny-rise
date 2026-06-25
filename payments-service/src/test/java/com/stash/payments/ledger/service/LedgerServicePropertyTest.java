package com.stash.payments.ledger.service;

import com.stash.payments.ledger.domain.EntryDirection;
import com.stash.payments.ledger.domain.EntryRequest;
import com.stash.payments.ledger.repository.LedgerEntryRepository;
import com.stash.payments.ledger.repository.LedgerTransactionRepository;
import com.stash.payments.outbox.service.OutboxPublisher;
import net.jqwik.api.*;
import org.mockito.Mockito;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Property-based test: any randomly generated balanced set of entries
 * must post successfully with no exceptions. Runs 10K tries by default.
 */
class LedgerServicePropertyTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-06-24T10:00:00Z"), ZoneOffset.UTC);

    @Property(tries = 10_000)
    @Label("10K randomly generated balanced transactions all post without exceptions")
    void any_balanced_transaction_posts(@ForAll("balancedEntries") List<EntryRequest> entries) {
        LedgerTransactionRepository txnRepo   = Mockito.mock(LedgerTransactionRepository.class);
        LedgerEntryRepository       entryRepo = Mockito.mock(LedgerEntryRepository.class);
        OutboxPublisher             outbox    = Mockito.mock(OutboxPublisher.class);

        when(txnRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(entryRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(outbox.publish(any(), any())).thenReturn(null);

        LedgerService service = new LedgerService(txnRepo, entryRepo, outbox, FIXED_CLOCK);
        LedgerWriteCommand command = new LedgerWriteCommand(
                "TRANSFER", "STSH-202606-PROP" + UUID.randomUUID().toString().substring(0, 8),
                UUID.randomUUID(), "PEER_TRANSFER",
                entries, "corr-prop", "Property test"
        );

        assertThatCode(() -> service.writeTransaction(command))
                .as("Balanced transaction must never throw")
                .doesNotThrowAnyException();
    }

    /**
     * Generates balanced entry lists: a random debit total is split
     * into one or more debits, then mirrored with an equal credit total.
     */
    @Provide
    Arbitrary<List<EntryRequest>> balancedEntries() {
        return Arbitraries.longs().between(1, 10_000_000L).flatMap(total -> {
            int debitParts  = Arbitraries.integers().between(1, 4).sample();
            int creditParts = Arbitraries.integers().between(1, 4).sample();
            return splitInto(total, debitParts).flatMap(debits ->
                    splitInto(total, creditParts).map(credits -> {
                        List<EntryRequest> result = new ArrayList<>();
                        for (long d : debits) {
                            result.add(EntryRequest.of(UUID.randomUUID(), EntryDirection.DEBIT, d));
                        }
                        for (long c : credits) {
                            result.add(EntryRequest.of(UUID.randomUUID(), EntryDirection.CREDIT, c));
                        }
                        return (List<EntryRequest>) result;
                    }));
        });
    }

    /**
     * Splits {@code total} into {@code parts} random positive longs summing to {@code total}.
     */
    private Arbitrary<List<Long>> splitInto(long total, int parts) {
        // Can't split N into more than N positive-valued parts (min 1 each)
        int safeParts = (int) Math.min(parts, total);
        if (safeParts <= 1) {
            return Arbitraries.just(List.of(total));
        }
        return Arbitraries.longs().between(1, total - (safeParts - 1))
                .list().ofSize(safeParts - 1)
                .map(cuts -> {
                    var sorted = cuts.stream().sorted().toList();
                    List<Long> chunks = new ArrayList<>();
                    long prev = 0;
                    for (long cut : sorted) {
                        long chunk = cut - prev;
                        chunks.add(chunk > 0 ? chunk : 1L);
                        prev = cut;
                    }
                    chunks.add(total - sorted.get(sorted.size() - 1));
                    // Correct any rounding drift so sum == total
                    long sum = chunks.stream().mapToLong(Long::longValue).sum();
                    if (sum != total) {
                        chunks.set(chunks.size() - 1,
                                chunks.get(chunks.size() - 1) + (total - sum));
                    }
                    return chunks;
                });
    }
}
