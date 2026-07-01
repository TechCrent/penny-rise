package com.stash.platform.transfer.service;

import com.stash.platform.transfer.api.dto.CreateTransferRequest;
import com.stash.platform.transfer.api.dto.CreateTransferResponse;
import com.stash.platform.transfer.client.PeerTransferPaymentsClient;
import com.stash.platform.transfer.client.TransferPaymentsException;
import com.stash.platform.transfer.domain.MonthlyTransferQuotaEntity;
import com.stash.platform.transfer.domain.PeerTransferEntity;
import com.stash.platform.transfer.repository.MonthlyTransferQuotaRepository;
import com.stash.platform.transfer.repository.PeerTransferRepository;
import com.stash.platform.user.domain.KycStatus;
import com.stash.platform.user.domain.User;
import com.stash.platform.user.repository.UserRepository;
import org.junit.jupiter.api.*;
import org.mockito.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.*;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.http.HttpStatus.*;

class PeerTransferServiceTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-06-24T10:00:00Z"), ZoneOffset.UTC);

    private final PeerTransferRepository         transferRepo   = Mockito.mock(PeerTransferRepository.class);
    private final MonthlyTransferQuotaRepository quotaRepo      = Mockito.mock(MonthlyTransferQuotaRepository.class);
    private final UserRepository                 userRepo       = Mockito.mock(UserRepository.class);
    private final PeerTransferPaymentsClient     paymentsClient = Mockito.mock(PeerTransferPaymentsClient.class);

    private final PeerTransferService service = new PeerTransferService(
            transferRepo, quotaRepo, userRepo, paymentsClient, FIXED_CLOCK, 200L);

    private static final UUID   SENDER_ID    = UUID.randomUUID();
    private static final UUID   RECIPIENT_ID = UUID.randomUUID();
    private static final UUID   SENDER_WALLET= UUID.randomUUID();
    private static final UUID   RECIP_WALLET = UUID.randomUUID();
    private static final String CORR              = "corr-transfer-001";
    private static final String IDEM_KEY          = "idem-transfer-001";
    private static final String PRINCIPAL_TXN_REF = "a1b2c3d4-0000-0000-0000-000000000001";

    @BeforeEach
    void setUp() {
        when(transferRepo.findByIdempotencyKey(IDEM_KEY)).thenReturn(Optional.empty());
        when(userRepo.findById(SENDER_ID)).thenReturn(Optional.of(approvedUser(SENDER_ID)));
        when(userRepo.findById(RECIPIENT_ID)).thenReturn(Optional.of(approvedUser(RECIPIENT_ID)));
        when(quotaRepo.findByUserAndMonthForUpdate(SENDER_ID, 2026, 6))
                .thenReturn(Optional.of(freshQuota()));
        when(quotaRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(transferRepo.save(any())).thenAnswer(inv -> {
            PeerTransferEntity t = inv.getArgument(0);
            if (t.getId() == null) setField(t, "id", UUID.randomUUID());
            return t;
        });
        when(paymentsClient.resolveUserWallet(SENDER_ID, CORR)).thenReturn(SENDER_WALLET);
        when(paymentsClient.resolveUserWallet(RECIPIENT_ID, CORR)).thenReturn(RECIP_WALLET);
        when(paymentsClient.transferPrincipal(any(), any(), anyLong(), any(), any(), any(), any()))
                .thenReturn(PRINCIPAL_TXN_REF);
    }

    // ── Happy: free transfer ───────────────────────────────────────────────

    @Test
    @DisplayName("free transfer (quota < 5): fee = 0, free_transfers_remaining decremented")
    void free_transfer_no_fee() {
        CreateTransferResponse result = service.transfer(
                SENDER_ID, request(50_000L), CORR, IDEM_KEY);

        assertThat(result.feeAmount()).isEqualTo(0L);
        assertThat(result.totalDebited()).isEqualTo(50_000L);
        assertThat(result.transactionReference()).isEqualTo(PRINCIPAL_TXN_REF);
        assertThat(result.status()).isEqualTo("COMPLETED");
    }

    @Test
    @DisplayName("free transfer: principal called, fee transfer NOT called")
    void free_transfer_no_fee_call() {
        service.transfer(SENDER_ID, request(50_000L), CORR, IDEM_KEY);

        verify(paymentsClient).transferPrincipal(any(), any(), eq(50_000L), any(), any(), any(), any());
        verify(paymentsClient, never()).transferFee(any(), anyLong(), any(), any(), any());
    }

    @Test
    @DisplayName("free transfer: free_transfers_remaining = 4 after first use")
    void free_transfers_remaining_decrements() {
        CreateTransferResponse result = service.transfer(
                SENDER_ID, request(50_000L), CORR, IDEM_KEY);

        assertThat(result.freeTransfersRemaining()).isEqualTo(4);
    }

    // ── Happy: paid transfer ──────────────────────────────────────────────

    @Test
    @DisplayName("paid transfer (quota >= 5): fee = 200p, fee transfer called")
    void paid_transfer_fee_charged() {
        when(quotaRepo.findByUserAndMonthForUpdate(SENDER_ID, 2026, 6))
                .thenReturn(Optional.of(exhaustedQuota()));

        CreateTransferResponse result = service.transfer(
                SENDER_ID, request(50_000L), CORR, IDEM_KEY);

        assertThat(result.feeAmount()).isEqualTo(200L);
        assertThat(result.totalDebited()).isEqualTo(50_200L);
        assertThat(result.freeTransfersRemaining()).isEqualTo(0);
    }

    @Test
    @DisplayName("paid transfer: both principal and fee transfer called")
    void paid_transfer_both_legs_called() {
        when(quotaRepo.findByUserAndMonthForUpdate(SENDER_ID, 2026, 6))
                .thenReturn(Optional.of(exhaustedQuota()));

        service.transfer(SENDER_ID, request(50_000L), CORR, IDEM_KEY);

        verify(paymentsClient).transferPrincipal(any(), any(), eq(50_000L), any(), any(), any(), any());
        verify(paymentsClient).transferFee(any(), eq(200L), any(), any(), any());
    }

    @Test
    @DisplayName("paid transfer: paid_transfers_count incremented, not free_transfers_used")
    void paid_quota_counter_incremented() {
        MonthlyTransferQuotaEntity exhausted = exhaustedQuota();
        when(quotaRepo.findByUserAndMonthForUpdate(SENDER_ID, 2026, 6))
                .thenReturn(Optional.of(exhausted));

        service.transfer(SENDER_ID, request(50_000L), CORR, IDEM_KEY);

        assertThat(exhausted.getFreeTransfersUsed()).isEqualTo(5);   // unchanged
        assertThat(exhausted.getPaidTransfersCount()).isEqualTo(1);  // incremented
    }

    // ── Validation failures ───────────────────────────────────────────────

    @Test
    @DisplayName("self-transfer returns 422 TRANSFER_TO_SELF")
    void self_transfer_returns_422() {
        assertThatThrownBy(() ->
                service.transfer(SENDER_ID,
                        new CreateTransferRequest(SENDER_ID, 50_000L, null),
                        CORR, IDEM_KEY))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    var e = (ResponseStatusException) ex;
                    assertThat(e.getStatusCode()).isEqualTo(UNPROCESSABLE_ENTITY);
                    assertThat(e.getReason()).contains("TRANSFER_TO_SELF");
                });
        verifyNoInteractions(paymentsClient);
    }

    @Test
    @DisplayName("recipient not found returns 404 RECIPIENT_NOT_FOUND")
    void recipient_not_found() {
        when(userRepo.findById(RECIPIENT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                service.transfer(SENDER_ID, request(50_000L), CORR, IDEM_KEY))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    var e = (ResponseStatusException) ex;
                    assertThat(e.getStatusCode()).isEqualTo(NOT_FOUND);
                    assertThat(e.getReason()).contains("RECIPIENT_NOT_FOUND");
                });
    }

    @Test
    @DisplayName("recipient KYC not approved returns 422 RECIPIENT_NOT_VERIFIED")
    void recipient_not_verified() {
        when(userRepo.findById(RECIPIENT_ID))
                .thenReturn(Optional.of(pendingKycUser(RECIPIENT_ID)));

        assertThatThrownBy(() ->
                service.transfer(SENDER_ID, request(50_000L), CORR, IDEM_KEY))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    var e = (ResponseStatusException) ex;
                    assertThat(e.getStatusCode()).isEqualTo(UNPROCESSABLE_ENTITY);
                    assertThat(e.getReason()).contains("RECIPIENT_NOT_VERIFIED");
                });
        verifyNoInteractions(paymentsClient);
    }

    @Test
    @DisplayName("sender KYC not approved returns 403")
    void sender_not_kyc_returns_403() {
        when(userRepo.findById(SENDER_ID))
                .thenReturn(Optional.of(pendingKycUser(SENDER_ID)));

        assertThatThrownBy(() ->
                service.transfer(SENDER_ID, request(50_000L), CORR, IDEM_KEY))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(FORBIDDEN));
    }

    // ── Insufficient balance ───────────────────────────────────────────────

    @Test
    @DisplayName("Payments 422 PAYMENTS_INSUFFICIENT_BALANCE → 422 TRANSFER_INSUFFICIENT_BALANCE")
    void insufficient_balance_translated() {
        when(paymentsClient.transferPrincipal(any(), any(), anyLong(), any(), any(), any(), any()))
                .thenThrow(new TransferPaymentsException(
                        "422:{\"code\":\"PAYMENTS_INSUFFICIENT_BALANCE\"}"));

        assertThatThrownBy(() ->
                service.transfer(SENDER_ID, request(50_000L), CORR, IDEM_KEY))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    var e = (ResponseStatusException) ex;
                    assertThat(e.getStatusCode()).isEqualTo(UNPROCESSABLE_ENTITY);
                    assertThat(e.getReason()).contains("TRANSFER_INSUFFICIENT_BALANCE");
                });
    }

    // ── Idempotent retry ───────────────────────────────────────────────────

    @Test
    @DisplayName("same idempotency key for COMPLETED transfer: cached response returned")
    void idempotent_retry_returns_cached() {
        PeerTransferEntity completed = PeerTransferEntity.create(
                SENDER_ID, RECIPIENT_ID, 50_000L, 0L, null, IDEM_KEY,
                Instant.parse("2026-06-24T09:00:00Z"));
        setField(completed, "id", UUID.randomUUID());
        completed.complete(UUID.randomUUID(), true,
                Instant.parse("2026-06-24T09:00:01Z"));
        when(transferRepo.findByIdempotencyKey(IDEM_KEY)).thenReturn(Optional.of(completed));

        CreateTransferResponse result = service.transfer(
                SENDER_ID, request(50_000L), CORR, IDEM_KEY);

        assertThat(result.status()).isEqualTo("COMPLETED");
        verifyNoInteractions(paymentsClient);
    }

    @Test
    @DisplayName("same idempotency key for PENDING transfer: 409 TRANSFER_IN_PROGRESS")
    void idempotent_pending_returns_409() {
        PeerTransferEntity pending = PeerTransferEntity.create(
                SENDER_ID, RECIPIENT_ID, 50_000L, 0L, null, IDEM_KEY,
                Instant.parse("2026-06-24T09:00:00Z"));
        setField(pending, "id", UUID.randomUUID());
        when(transferRepo.findByIdempotencyKey(IDEM_KEY)).thenReturn(Optional.of(pending));

        assertThatThrownBy(() ->
                service.transfer(SENDER_ID, request(50_000L), CORR, IDEM_KEY))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    var e = (ResponseStatusException) ex;
                    assertThat(e.getStatusCode()).isEqualTo(CONFLICT);
                    assertThat(e.getReason()).contains("TRANSFER_IN_PROGRESS");
                });
    }

    // ── New quota row created lazily ───────────────────────────────────────

    @Test
    @DisplayName("no existing quota row: insertIfAbsent called, transfer succeeds")
    void quota_row_created_lazily() {
        // setUp mocks findByUserAndMonthForUpdate to return freshQuota(),
        // which represents the state after insertIfAbsent creates the row.
        service.transfer(SENDER_ID, request(50_000L), CORR, IDEM_KEY);

        verify(quotaRepo).insertIfAbsent(any(UUID.class), eq(SENDER_ID), anyInt(), anyInt());
        verify(quotaRepo, atLeastOnce()).save(any(MonthlyTransferQuotaEntity.class));
    }

    // ── Idempotency keys for Payments legs ────────────────────────────────

    @Test
    @DisplayName("principal idempotency key includes transfer ID")
    void principal_idempotency_key_stable() {
        service.transfer(SENDER_ID, request(50_000L), CORR, IDEM_KEY);

        ArgumentCaptor<String> idemCaptor = ArgumentCaptor.forClass(String.class);
        verify(paymentsClient).transferPrincipal(any(), any(), anyLong(), any(), any(), any(),
                idemCaptor.capture());
        assertThat(idemCaptor.getValue()).startsWith("peer-transfer-");
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private static CreateTransferRequest request(long amount) {
        return new CreateTransferRequest(RECIPIENT_ID, amount, "Test payment");
    }

    private static User approvedUser(UUID id) {
        User u = new User();
        u.setId(id);
        u.setKycStatus(KycStatus.APPROVED);
        return u;
    }

    private static User pendingKycUser(UUID id) {
        User u = new User();
        u.setId(id);
        u.setKycStatus(KycStatus.PENDING);
        return u;
    }

    private static MonthlyTransferQuotaEntity freshQuota() {
        return MonthlyTransferQuotaEntity.create(SENDER_ID, 2026, 6);
    }

    private static MonthlyTransferQuotaEntity exhaustedQuota() {
        MonthlyTransferQuotaEntity q = MonthlyTransferQuotaEntity.create(SENDER_ID, 2026, 6);
        for (int i = 0; i < 5; i++) q.consumeOneTransfer(5);
        return q;
    }

    private static void setField(Object obj, String name, Object value) {
        try {
            var f = findField(obj.getClass(), name);
            f.setAccessible(true);
            f.set(obj, value);
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    private static java.lang.reflect.Field findField(Class<?> c, String name)
            throws NoSuchFieldException {
        try { return c.getDeclaredField(name); }
        catch (NoSuchFieldException e) {
            if (c.getSuperclass() != null) return findField(c.getSuperclass(), name);
            throw e;
        }
    }
}
