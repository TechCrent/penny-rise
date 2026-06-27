package com.stash.payments.paystack.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stash.payments.ledger.repository.LedgerAccountRepository;
import com.stash.payments.paystack.client.PaystackClient;
import com.stash.payments.paystack.domain.PaystackSubaccountEntity;
import com.stash.payments.paystack.dto.SubaccountCreateRequest;
import com.stash.payments.paystack.dto.SubaccountCreateResponse;
import com.stash.payments.paystack.repository.PaystackSubaccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Calls the Paystack API to create a subaccount and stores the result
 * in {@code paystack.paystack_sub_accounts}. Also updates
 * {@code ledger_accounts.external_reference} with the subaccount code.
 *
 * <p>Called by {@link com.stash.payments.paystack.consumer.PaystackSubaccountProvisionConsumer}
 * after the outbox relay delivers the {@code paystack.subaccount.provision.requested}
 * event. Because the Paystack call happens outside the provisioning transaction,
 * a transient Paystack failure only affects this step — the ledger account
 * is already committed and safe.
 */
@Service
public class PaystackSubaccountService {

    private static final Logger log = LoggerFactory.getLogger(PaystackSubaccountService.class);
    private static final String OWNER_TYPE   = "USER";
    private static final double DEFAULT_CHARGE_PERCENT = 0.0;  // Stash collects fees separately

    private final PaystackClient               paystackClient;
    private final PaystackSubaccountRepository subaccountRepository;
    private final LedgerAccountRepository      ledgerAccountRepository;
    private final ObjectMapper                 objectMapper;
    private final Clock                        clock;

    public PaystackSubaccountService(PaystackClient paystackClient,
                                     PaystackSubaccountRepository subaccountRepository,
                                     LedgerAccountRepository ledgerAccountRepository,
                                     ObjectMapper objectMapper,
                                     Clock clock) {
        this.paystackClient          = paystackClient;
        this.subaccountRepository    = subaccountRepository;
        this.ledgerAccountRepository = ledgerAccountRepository;
        this.objectMapper            = objectMapper;
        this.clock                   = clock;
    }

    /**
     * Provisions a Paystack subaccount for a user and links it to their
     * ledger account. Idempotent — safe to call if the relay retries.
     *
     * @param ledgerAccountId the UUID of the already-committed USER_WALLET account
     * @param userId          the user's UUID (logical reference to monolith)
     * @param userEmail       used as the Paystack business_name (displayable name)
     * @param correlationId   trace ID for logging
     */
    @Transactional
    public void provisionSubaccount(UUID ledgerAccountId, UUID userId,
                                    String userEmail, String correlationId) {
        // ── Idempotency: already provisioned? ─────────────────────────────
        if (subaccountRepository.existsByOwnerTypeAndOwnerId(OWNER_TYPE, userId)) {
            log.info("Paystack subaccount already exists for user={} — no-op. correlation={}",
                    userId, correlationId);
            return;
        }

        // ── Call Paystack ─────────────────────────────────────────────────
        // businessName: use the email as a display name until full name is available.
        // Paystack requires something per subaccount — email is unique per user.
        SubaccountCreateRequest request = new SubaccountCreateRequest(
                "Stash/" + userEmail,
                "TEST",              // Paystack test bank code for sandbox
                "0000000000",        // Paystack test account number for sandbox
                DEFAULT_CHARGE_PERCENT,
                "USER_WALLET for " + userId
        );

        // PaystackClient.createSubaccount is retried on 5xx via the idempotent
        // resilience policy. If it throws after retries, the exception propagates
        // to the outbox consumer, which requeues via RabbitMQ.
        SubaccountCreateResponse response = paystackClient.createSubaccount(request);

        if (!response.status()) {
            throw new PaystackSubaccountCreationException(
                    "Paystack returned status=false for subaccount creation. " +
                    "userId=" + userId + " message=" + response.message());
        }

        String subaccountCode = response.data().subaccountCode();

        // ── Store metadata ────────────────────────────────────────────────
        String metadata = serialiseMetadata(response.data());

        PaystackSubaccountEntity subaccount = new PaystackSubaccountEntity(
                OWNER_TYPE, userId, subaccountCode, ledgerAccountId, metadata,
                Instant.now(clock)
        );
        subaccountRepository.save(subaccount);

        // ── Update ledger account external_reference ──────────────────────
        ledgerAccountRepository.findById(ledgerAccountId).ifPresent(account -> {
            account.setExternalReference(subaccountCode);
            ledgerAccountRepository.save(account);
        });

        log.info("Paystack subaccount provisioned: code={} user={} ledgerAccount={} correlation={}",
                subaccountCode, userId, ledgerAccountId, correlationId);
    }

    private String serialiseMetadata(SubaccountCreateResponse.SubaccountData data) {
        try {
            return objectMapper.writeValueAsString(data);
        } catch (Exception e) {
            log.warn("Failed to serialise Paystack subaccount metadata — storing null. error={}",
                    e.getMessage());
            return null;
        }
    }
}
