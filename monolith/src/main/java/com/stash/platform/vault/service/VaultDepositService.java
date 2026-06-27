package com.stash.platform.vault.service;

import com.stash.platform.user.domain.User;
import com.stash.platform.user.repository.UserRepository;
import com.stash.platform.vault.api.dto.VaultDepositRequest;
import com.stash.platform.vault.api.dto.VaultDepositResponse;
import com.stash.platform.vault.client.PaymentsDepositClient;
import com.stash.platform.vault.client.PaymentsServiceException;
import com.stash.platform.vault.domain.VaultEntity;
import com.stash.platform.vault.repository.VaultRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/**
 * Orchestrates vault deposits.
 *
 * <p>This service is intentionally thin — it owns the monolith-side
 * validation (vault ownership, vault status) and nothing else. All money
 * movement is delegated to the Payments Service.
 *
 * <p><strong>What this service does:</strong>
 * <ol>
 *   <li>Validates the vault exists and belongs to the authenticated user.</li>
 *   <li>Validates the vault is in a depositable status (ACTIVE or EARLY_EXIT_PENDING).</li>
 *   <li>Loads the user's email (required by Paystack for charge initiation).</li>
 *   <li>Delegates to the Payments Service deposit endpoint.</li>
 *   <li>Returns the Paystack authorisation URL to the caller.</li>
 * </ol>
 *
 * <p><strong>What this service does NOT do:</strong>
 * <ul>
 *   <li>Touch the ledger — Payments owns all ledger writes.</li>
 *   <li>Call Paystack — Payments owns the Paystack integration.</li>
 *   <li>Handle webhook confirmation — Payments owns webhook processing.</li>
 * </ul>
 */
@Service
public class VaultDepositService {

    private static final Logger log = LoggerFactory.getLogger(VaultDepositService.class);

    private final VaultRepository       vaultRepo;
    private final UserRepository        userRepo;
    private final PaymentsDepositClient paymentsClient;

    public VaultDepositService(VaultRepository vaultRepo,
                                UserRepository userRepo,
                                PaymentsDepositClient paymentsClient) {
        this.vaultRepo      = vaultRepo;
        this.userRepo       = userRepo;
        this.paymentsClient = paymentsClient;
    }

    /**
     * Initiates a deposit into a vault.
     *
     * @param vaultId        the vault to deposit into
     * @param userId         authenticated user (from JWT)
     * @param request        validated deposit request
     * @param correlationId  trace ID
     * @param idempotencyKey from the Idempotency-Key header
     * @return the Payments Service response forwarded to the caller
     * @throws ResponseStatusException 403 if vault doesn't belong to user;
     *                                 404 if vault not found;
     *                                 409 if vault is CLOSED;
     *                                 422 for invalid request;
     *                                 502 if Payments Service is unavailable
     */
    @Transactional(readOnly = true)
    public VaultDepositResponse initiateDeposit(UUID vaultId, UUID userId,
                                                 VaultDepositRequest request,
                                                 String correlationId,
                                                 String idempotencyKey) {
        validatePaymentMethod(request);

        VaultEntity vault = vaultRepo.findById(vaultId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Vault not found: " + vaultId));

        if (!userId.equals(vault.getOwnerUserId())) {
            log.warn("Deposit ownership mismatch: vault={} requesting_user={} vault_owner={}",
                    vaultId, userId, vault.getOwnerUserId());
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Vault does not belong to the authenticated user.");
        }

        if (vault.getDeletedAt() != null || "CLOSED".equals(vault.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "VAULT_CLOSED: Cannot deposit into a closed vault.");
        }

        if (!"ACTIVE".equals(vault.getStatus()) && !"EARLY_EXIT_PENDING".equals(vault.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cannot deposit into a vault with status: " + vault.getStatus());
        }

        User user = userRepo.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "Authenticated user not found — this should not happen."));

        try {
            PaymentsDepositClient.DepositResult result = paymentsClient.initiateDeposit(
                    userId,
                    user.getEmail(),
                    vault.getLedgerAccountId(),
                    request.amount(),
                    request.paymentMethod(),
                    request.mobileNumber(),
                    request.mobileProvider(),
                    vaultId,
                    correlationId,
                    idempotencyKey
            );

            log.info("Vault deposit initiated: vault={} txnRef={} amount={}p user={} correlation={}",
                    vaultId, result.transactionReference(), request.amount(), userId, correlationId);

            return new VaultDepositResponse(
                    result.transactionReference(),
                    result.authorisationUrl(),
                    result.paystackReference(),
                    result.status()
            );

        } catch (PaymentsServiceException e) {
            if (e.getHttpStatus() >= 400 && e.getHttpStatus() < 500) {
                throw new ResponseStatusException(HttpStatus.valueOf(e.getHttpStatus()),
                        e.getMessage());
            }
            log.error("Payments Service error during vault deposit: vault={} error={} correlation={}",
                    vaultId, e.getMessage(), correlationId);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Payment processing temporarily unavailable. Please retry.");
        }
    }

    private void validatePaymentMethod(VaultDepositRequest request) {
        if (!"MOMO".equalsIgnoreCase(request.paymentMethod())
                && !"CARD".equalsIgnoreCase(request.paymentMethod())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "payment_method must be MOMO or CARD.");
        }
        if ("MOMO".equalsIgnoreCase(request.paymentMethod())) {
            if (request.mobileNumber() == null || request.mobileNumber().isBlank()) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "mobile_number is required for MOMO deposits.");
            }
            if (request.mobileProvider() == null || request.mobileProvider().isBlank()) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "mobile_provider is required for MOMO deposits.");
            }
        }
    }
}
