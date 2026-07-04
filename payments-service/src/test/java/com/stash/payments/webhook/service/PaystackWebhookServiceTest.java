package com.stash.payments.webhook.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stash.payments.transaction.service.WithdrawalService;
import com.stash.payments.webhook.repository.ProcessedWebhookEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PaystackWebhookServiceTest {

    private static final String VALID_SIG    = "valid-hmac-signature";
    private static final String CORRELATION  = "corr-test-001";
    private static final Clock  FIXED_CLOCK  =
            Clock.fixed(Instant.parse("2024-06-01T10:00:00Z"), ZoneOffset.UTC);

    @Mock private ProcessedWebhookEventRepository webhookEventRepo;
    @Mock private ChargeSuccessHandler            chargeSuccessHandler;
    @Mock private WithdrawalService               withdrawalService;

    private final PaystackWebhookVerifier verifier     = (rawBody, sig) -> VALID_SIG.equals(sig);
    private final ObjectMapper            objectMapper = new ObjectMapper();

    private PaystackWebhookService service;

    @BeforeEach
    void setUp() {
        service = new PaystackWebhookService(
                verifier, webhookEventRepo, chargeSuccessHandler, withdrawalService,
                objectMapper, FIXED_CLOCK);
        when(webhookEventRepo.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(webhookEventRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void invalidSignature_returnsFalse() {
        byte[] body = chargeSuccessBody("ref_001");
        boolean result = service.process(body, "wrong-sig", CORRELATION);
        assertThat(result).isFalse();
        verifyNoInteractions(webhookEventRepo, chargeSuccessHandler);
    }

    @Test
    void nullSignature_returnsFalse() {
        byte[] body = chargeSuccessBody("ref_002");
        boolean result = service.process(body, null, CORRELATION);
        assertThat(result).isFalse();
        verifyNoInteractions(webhookEventRepo, chargeSuccessHandler);
    }

    @Test
    void duplicateDelivery_returnsTrue() {
        when(webhookEventRepo.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("unique constraint"));

        byte[] body = chargeSuccessBody("ref_003");
        boolean result = service.process(body, VALID_SIG, CORRELATION);

        assertThat(result).isTrue();
        verifyNoInteractions(chargeSuccessHandler);
    }

    @Test
    void chargeSuccess_happyPath_returnsTrue() {
        UUID txnId = UUID.randomUUID();
        when(chargeSuccessHandler.handle(any(), eq(CORRELATION))).thenReturn(txnId);

        byte[] body = chargeSuccessBody("ref_004");
        boolean result = service.process(body, VALID_SIG, CORRELATION);

        assertThat(result).isTrue();
        verify(webhookEventRepo).saveAndFlush(any());
        verify(chargeSuccessHandler).handle(any(), eq(CORRELATION));
    }

    @Test
    void handlerFailure_rethrows() {
        when(chargeSuccessHandler.handle(any(), any()))
                .thenThrow(new IllegalStateException("no USER_WALLET found"));

        byte[] body = chargeSuccessBody("ref_005");
        assertThatThrownBy(() -> service.process(body, VALID_SIG, CORRELATION))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Webhook processing failed");
    }

    @Test
    void transferSuccess_routesToWithdrawalServiceAndReturnsTrue() {
        UUID txnId = UUID.randomUUID();
        when(withdrawalService.handleTransferSuccess(eq("TRF_success_001"), anyLong(), eq(CORRELATION)))
                .thenReturn(txnId);

        byte[] body = eventBodyWithTransferCode("transfer.success", "TRF_success_001");
        boolean result = service.process(body, VALID_SIG, CORRELATION);

        assertThat(result).isTrue();
        verify(withdrawalService).handleTransferSuccess(eq("TRF_success_001"), anyLong(), eq(CORRELATION));
        verifyNoInteractions(chargeSuccessHandler);
    }

    @Test
    void transferFailed_routesToWithdrawalServiceAndReturnsTrue() {
        UUID txnId = UUID.randomUUID();
        when(withdrawalService.handleTransferFailed(eq("TRF_failed_001"), any(), eq(CORRELATION)))
                .thenReturn(txnId);

        byte[] body = eventBodyWithTransferCode("transfer.failed", "TRF_failed_001");
        boolean result = service.process(body, VALID_SIG, CORRELATION);

        assertThat(result).isTrue();
        verify(withdrawalService).handleTransferFailed(eq("TRF_failed_001"), any(), eq(CORRELATION));
        verifyNoInteractions(chargeSuccessHandler);
    }

    @Test
    void unknownEventType_returnsTrue() {
        byte[] body = eventBody("subscription.create", "sub_001");
        boolean result = service.process(body, VALID_SIG, CORRELATION);
        assertThat(result).isTrue();
        verifyNoInteractions(chargeSuccessHandler);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static byte[] chargeSuccessBody(String reference) {
        return """
                {"id":"evt-123","event":"charge.success","data":{"id":99,"reference":"%s","amount":5000}}
                """.formatted(reference).getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] eventBody(String eventType, String reference) {
        return """
                {"id":"evt-456","event":"%s","data":{"id":100,"reference":"%s","amount":2000}}
                """.formatted(eventType, reference).getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] eventBodyWithTransferCode(String eventType, String transferCode) {
        return """
                {"id":"evt-789","event":"%s","data":{"id":101,"transfer_code":"%s","amount":2000,"gateway_response":"success"}}
                """.formatted(eventType, transferCode).getBytes(StandardCharsets.UTF_8);
    }
}
