package com.stash.platform.user.service;

import com.stash.platform.transfer.client.PeerTransferPaymentsClient;
import com.stash.platform.transfer.client.TransferPaymentsException;
import com.stash.platform.user.domain.User;
import com.stash.platform.user.repository.UserRepository;
import com.stash.platform.vault.api.dto.VaultDepositRequest;
import com.stash.platform.vault.api.dto.VaultDepositResponse;
import com.stash.platform.vault.client.PaymentsDepositClient;
import com.stash.platform.vault.client.PaymentsServiceException;
import com.stash.shared.validation.MomoNumberValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
public class WalletDepositService {

    private static final Logger log = LoggerFactory.getLogger(WalletDepositService.class);

    private final UserRepository        userRepo;
    private final PaymentsDepositClient paymentsClient;
    private final PeerTransferPaymentsClient walletClient;

    public WalletDepositService(UserRepository userRepo,
                                PaymentsDepositClient paymentsClient,
                                PeerTransferPaymentsClient walletClient) {
        this.userRepo         = userRepo;
        this.paymentsClient   = paymentsClient;
        this.walletClient     = walletClient;
    }

    @Transactional(readOnly = true)
    public VaultDepositResponse initiateDeposit(UUID userId,
                                                VaultDepositRequest request,
                                                String correlationId,
                                                String idempotencyKey) {
        validatePaymentMethod(request);

        User user = userRepo.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "Authenticated user not found."));

        UUID walletAccountId;
        try {
            walletAccountId = walletClient.resolveUserWallet(userId, correlationId);
        } catch (TransferPaymentsException e) {
            log.error("Cannot resolve USER_WALLET for wallet deposit: user={} error={} correlation={}",
                    userId, e.getMessage(), correlationId);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Payment processing temporarily unavailable. Please retry.");
        }

        try {
            PaymentsDepositClient.DepositResult result = paymentsClient.initiateWalletDeposit(
                    userId,
                    user.getEmail(),
                    walletAccountId,
                    request.amount(),
                    request.paymentMethod(),
                    request.mobileNumber(),
                    request.mobileProvider(),
                    correlationId,
                    idempotencyKey
            );

            log.info("Wallet deposit initiated: txnRef={} amount={}p user={} correlation={}",
                    result.transactionReference(), request.amount(), userId, correlationId);

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
            log.error("Payments Service error during wallet deposit: user={} error={} correlation={}",
                    userId, e.getMessage(), correlationId);
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
            MomoNumberValidator.validate(request.mobileProvider(), request.mobileNumber(),
                    "mobile_provider", "mobile_number");
        }
    }
}
