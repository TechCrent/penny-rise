package com.stash.platform.vault.service;

import com.stash.platform.vault.api.dto.VaultListItemResponse;
import com.stash.platform.vault.api.dto.VaultListResponse;
import com.stash.platform.vault.client.PaymentsBalanceClient;
import com.stash.platform.vault.domain.VaultEntity;
import com.stash.platform.vault.repository.VaultRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Returns the vault list for the authenticated user, enriched with balances.
 *
 * <p><strong>Balance fan-out:</strong> all balance requests to the Payments Service
 * fire in parallel using virtual threads (Java 21). Each vault's balance is fetched
 * independently. Any failed fetch results in {@code balance = null} for that vault —
 * the rest of the list is unaffected.
 *
 * <p><strong>Ordering:</strong> the database query returns vaults newest-first
 * ({@code ORDER BY created_at DESC}). The parallel balance enrichment preserves
 * this ordering by mapping results back to their original position.
 *
 * <p><strong>Timeout:</strong> each balance call is bounded to 3 seconds by the
 * {@link PaymentsBalanceClient}. The fan-out itself waits up to 4 seconds total
 * (1 second slack) before giving up on any remaining calls and returning partial results.
 */
@Service
public class VaultListService {

    private static final Logger log = LoggerFactory.getLogger(VaultListService.class);
    private static final long   FAN_OUT_TIMEOUT_MS = 4_000L;

    private final VaultRepository       vaultRepo;
    private final PaymentsBalanceClient balanceClient;
    private final Executor              fanOutExecutor;

    public VaultListService(VaultRepository vaultRepo,
                             PaymentsBalanceClient balanceClient) {
        this.vaultRepo      = vaultRepo;
        this.balanceClient  = balanceClient;
        this.fanOutExecutor = Executors.newVirtualThreadPerTaskExecutor();
    }

    /**
     * Returns the authenticated user's vaults with balance enrichment.
     *
     * @param userId        authenticated user's UUID
     * @param includeClosed if true, CLOSED vaults are included for history display
     * @param correlationId trace ID for logging and Payments Service calls
     */
    @Transactional(readOnly = true)
    public VaultListResponse listVaults(UUID userId, boolean includeClosed,
                                         String correlationId) {
        List<VaultEntity> vaults = vaultRepo.findVaultsForUser(userId, includeClosed);

        if (vaults.isEmpty()) {
            log.debug("Vault list: no vaults for user={}", userId);
            return VaultListResponse.of(List.of(), 0);
        }

        List<CompletableFuture<Optional<Long>>> futures = vaults.stream()
                .map(v -> CompletableFuture.supplyAsync(
                        () -> balanceClient.fetchBalance(v.getLedgerAccountId(), correlationId),
                        fanOutExecutor))
                .toList();

        try {
            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
                    .get(FAN_OUT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (java.util.concurrent.TimeoutException e) {
            log.warn("Vault list balance fan-out timed out after {}ms for user={} correlation={}",
                     FAN_OUT_TIMEOUT_MS, userId, correlationId);
            futures.forEach(f -> f.cancel(true));
        } catch (Exception e) {
            log.warn("Vault list balance fan-out interrupted for user={} error={}",
                     userId, e.getMessage());
        }

        AtomicInteger unavailable = new AtomicInteger(0);
        List<VaultListItemResponse> items = new ArrayList<>(vaults.size());

        for (int i = 0; i < vaults.size(); i++) {
            VaultEntity vault  = vaults.get(i);
            CompletableFuture<Optional<Long>> future = futures.get(i);

            Long balancePesewas = null;
            try {
                Optional<Long> balance = future.isDone()
                        ? future.get()
                        : Optional.empty();
                if (balance.isPresent()) {
                    balancePesewas = balance.get();
                } else {
                    unavailable.incrementAndGet();
                }
            } catch (Exception e) {
                unavailable.incrementAndGet();
                log.debug("Balance future failed for vault={} error={}", vault.getId(), e.getMessage());
            }

            items.add(VaultListItemResponse.from(vault, balancePesewas));
        }

        log.debug("Vault list: user={} vaults={} balanceUnavailable={} correlation={}",
                  userId, items.size(), unavailable.get(), correlationId);

        return VaultListResponse.of(items, unavailable.get());
    }
}
