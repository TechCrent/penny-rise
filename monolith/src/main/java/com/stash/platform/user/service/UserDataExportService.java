package com.stash.platform.user.service;

import com.stash.platform.susu.service.SusuGroupQueryService;
import com.stash.platform.transaction.service.TransactionStatementExportService;
import com.stash.platform.user.api.dto.GdprDataExportResponse;
import com.stash.platform.user.api.dto.GdprVaultSummary;
import com.stash.platform.vault.domain.VaultEntity;
import com.stash.platform.vault.service.VaultService;
import com.stash.shared.correlation.CorrelationContext;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/**
 * "Download my data" (GDPR-style data portability) — gap-analysis fix:
 * account *deletion* existed (v0.2-019/v0.5-019) but data *portability*
 * did not. Scoped to what monolith can already assemble via its existing
 * internal clients/services for the caller's own resources — not a new
 * cross-service integration.
 */
@Service
public class UserDataExportService {

    private final UserProfileService userProfileService;
    private final VaultService vaultService;
    private final SusuGroupQueryService susuGroupQueryService;
    private final TransactionStatementExportService transactionExportService;

    public UserDataExportService(UserProfileService userProfileService,
                                 VaultService vaultService,
                                 SusuGroupQueryService susuGroupQueryService,
                                 TransactionStatementExportService transactionExportService) {
        this.userProfileService = userProfileService;
        this.vaultService = vaultService;
        this.susuGroupQueryService = susuGroupQueryService;
        this.transactionExportService = transactionExportService;
    }

    public GdprDataExportResponse export(UUID userId) {
        var vaults = vaultService.listForOwner(userId).stream()
                .map(this::toVaultSummary)
                .toList();

        var susuMemberships = susuGroupQueryService.listGroups(userId, true, CorrelationContext.get());

        var transactions = transactionExportService.fetchAll(userId);

        return new GdprDataExportResponse(
                userProfileService.getProfile(userId),
                vaults,
                susuMemberships,
                transactions,
                Instant.now()
        );
    }

    private GdprVaultSummary toVaultSummary(VaultEntity vault) {
        return new GdprVaultSummary(
                vault.getId(), vault.getName(), vault.getVaultType(),
                vault.getStatus(), vault.getCreatedAt());
    }
}
