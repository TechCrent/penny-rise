package com.stash.payments.transaction.service;

import com.stash.payments.ledger.domain.LedgerAccountEntity;
import com.stash.payments.ledger.repository.LedgerAccountRepository;
import com.stash.payments.ledger.service.TransactionReferenceGenerator;
import com.stash.payments.moolre.client.MoolreClient;
import com.stash.payments.moolre.dto.PaymentInitiateResult;
import com.stash.payments.moolre.exception.MoolreClientException;
import com.stash.payments.transaction.api.dto.DepositInitiateRequest;
import com.stash.payments.transaction.api.dto.DepositInitiateResponse;
import com.stash.payments.transaction.domain.TransactionEntity;
import com.stash.payments.transaction.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.http.HttpStatus.*;

class DepositServiceTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-06-24T10:00:00Z"), ZoneOffset.UTC);

    private final LedgerAccountRepository       ledgerRepo = Mockito.mock(LedgerAccountRepository.class);
    private final TransactionRepository         txnRepo    = Mockito.mock(TransactionRepository.class);
    private final MoolreClient                  moolre     = Mockito.mock(MoolreClient.class);
    private final TransactionReferenceGenerator refGen     = Mockito.mock(TransactionReferenceGenerator.class);
    private final DepositService service = new DepositService(
            ledgerRepo, txnRepo, moolre, refGen, FIXED_CLOCK, false);

    private static final UUID   USER_ID    = UUID.randomUUID();
    private static final UUID   ACCOUNT_ID = UUID.randomUUID();
    private static final String REF        = "STSH-202606-ABC123";
    private static final String IDEM_KEY   = "idem-deposit-001";

    @BeforeEach
    void setUp() {
        when(refGen.generate()).thenReturn(REF);
        when(txnRepo.findByReference(REF)).thenReturn(Optional.empty());
        when(txnRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("happy path: valid MoMo deposit returns 202 with PENDING status")
    void happy_path_returns_202_pending() {
        stubActiveAccount();
        stubMoolreSuccess();

        var result = service.initiateDeposit(momoRequest(10_000L), IDEM_KEY);

        assertThat(result.transactionReference()).isEqualTo(REF);
        assertThat(result.status()).isEqualTo("PENDING");
        assertThat(result.providerReference()).isEqualTo("session-001");
        assertThat(result.authorisationUrl()).contains("MoMo prompt");
    }

    @Test
    @DisplayName("PENDING transaction row saved before Moolre call")
    void pending_row_saved_before_moolre_call() {
        stubActiveAccount();
        stubMoolreSuccess();

        service.initiateDeposit(momoRequest(10_000L), IDEM_KEY);

        verify(txnRepo, atLeast(2)).save(any(TransactionEntity.class));
    }

    @Test
    @DisplayName("account not found returns 404")
    void account_not_found_returns_404() {
        when(ledgerRepo.findById(ACCOUNT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.initiateDeposit(momoRequest(10_000L), IDEM_KEY))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(NOT_FOUND));
    }

    @Test
    @DisplayName("account not ACTIVE returns 409")
    void closed_account_returns_409() {
        LedgerAccountEntity closed = closedAccount();
        when(ledgerRepo.findById(ACCOUNT_ID)).thenReturn(Optional.of(closed));

        assertThatThrownBy(() -> service.initiateDeposit(momoRequest(10_000L), IDEM_KEY))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(CONFLICT));
    }

    @Test
    @DisplayName("account owned by different user returns 403")
    void wrong_user_returns_403() {
        LedgerAccountEntity accountOwnedByOther = accountOwnedBy(UUID.randomUUID());
        when(ledgerRepo.findById(ACCOUNT_ID)).thenReturn(Optional.of(accountOwnedByOther));

        assertThatThrownBy(() -> service.initiateDeposit(momoRequest(10_000L), IDEM_KEY))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(FORBIDDEN));
    }

    @Test
    @DisplayName("VAULT account (owner_id = vaultId, not userId) does NOT 403")
    void vault_account_deposit_does_not_403() {
        LedgerAccountEntity vaultAccount = new LedgerAccountEntity(
                "VAULT", "VAULT", UUID.randomUUID(), "vault ledger", Instant.now(FIXED_CLOCK));
        when(ledgerRepo.findById(ACCOUNT_ID)).thenReturn(Optional.of(vaultAccount));
        stubMoolreSuccess();

        var result = service.initiateDeposit(momoRequest(10_000L), IDEM_KEY);

        assertThat(result.status()).isEqualTo("PENDING");
        assertThat(result.transactionReference()).isEqualTo(REF);
    }

    @Test
    @DisplayName("no subaccount lookup — deposits work without Paystack subaccounts")
    void no_subaccount_required() {
        stubActiveAccount();
        stubMoolreSuccess();

        var result = service.initiateDeposit(momoRequest(10_000L), IDEM_KEY);

        assertThat(result.status()).isEqualTo("PENDING");
        verify(moolre).initiatePayment(eq("13"), eq("233241234567"), eq("100.00"), eq(REF), eq(REF));
    }

    @Test
    @DisplayName("CARD payment method returns 501")
    void card_payment_method_returns_501() {
        var cardRequest = new DepositInitiateRequest(
                ACCOUNT_ID, USER_ID, 10_000L,
                "CARD", "akua@stash.test",
                null, null, "corr-001",
                UUID.randomUUID(), "VAULT_DEPOSIT");

        assertThatThrownBy(() -> service.initiateDeposit(cardRequest, IDEM_KEY))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(NOT_IMPLEMENTED));
    }

    @Test
    @DisplayName("missing mobile_number returns 422")
    void missing_mobile_number_returns_422() {
        stubActiveAccount();

        var request = new DepositInitiateRequest(
                ACCOUNT_ID, USER_ID, 10_000L,
                "MOMO", "akua@stash.test",
                null, "mtn", "corr-001",
                UUID.randomUUID(), "VAULT_DEPOSIT");

        assertThatThrownBy(() -> service.initiateDeposit(request, IDEM_KEY))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(UNPROCESSABLE_ENTITY));
    }

    @Test
    @DisplayName("substitution disabled: Moolre is charged with the real user-entered number")
    void substitution_disabled_charges_real_number() {
        stubActiveAccount();
        stubMoolreSuccess();
        when(moolre.isSandbox()).thenReturn(true);

        service.initiateDeposit(momoRequest(10_000L), IDEM_KEY);

        verify(moolre).initiatePayment(eq("13"), eq("233241234567"), anyString(), eq(REF), eq(REF));
    }

    @Test
    @DisplayName("substitution enabled + sandbox: charges placeholder test number")
    void substitution_enabled_in_sandbox_charges_placeholder() {
        var serviceWithSubstitution = new DepositService(
                ledgerRepo, txnRepo, moolre, refGen, FIXED_CLOCK, true);
        stubActiveAccount();
        stubMoolreSuccess();
        when(moolre.isSandbox()).thenReturn(true);

        var request = new DepositInitiateRequest(
                ACCOUNT_ID, USER_ID, 10_000L,
                "MOMO", "akua@stash.test",
                "0201234567", "vodafone", "corr-001",
                UUID.randomUUID(), "VAULT_DEPOSIT");

        serviceWithSubstitution.initiateDeposit(request, IDEM_KEY);

        verify(moolre).initiatePayment(eq("13"), eq("233000000000"), anyString(), eq(REF), eq(REF));
    }

    @Test
    @DisplayName("TP14 OTP required returns otp_required flag")
    void otp_required_leaves_pending() {
        stubActiveAccount();
        when(moolre.initiatePayment(any(), any(), any(), any(), any()))
                .thenReturn(new PaymentInitiateResult("TP14",
                        "Please complete verification", "all", true));

        DepositInitiateResponse response = service.initiateDeposit(momoRequest(10_000L), IDEM_KEY);

        assertThat(response.status()).isEqualTo("PENDING");
        assertThat(response.otpRequired()).isTrue();
        assertThat(response.transactionReference()).isEqualTo(REF);
        assertThat(response.authorisationUrl()).containsIgnoringCase("verification");
        verify(txnRepo, atLeastOnce()).save(any(TransactionEntity.class));
    }

    @Test
    @DisplayName("phone number not matching provider prefixes returns 422")
    void momo_number_not_matching_provider_returns_422() {
        stubActiveAccount();

        var request = new DepositInitiateRequest(
                ACCOUNT_ID, USER_ID, 10_000L,
                "MOMO", "akua@stash.test",
                "0201234567", "mtn", "corr-001",
                UUID.randomUUID(), "VAULT_DEPOSIT");

        assertThatThrownBy(() -> service.initiateDeposit(request, IDEM_KEY))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(UNPROCESSABLE_ENTITY));
    }

    @Test
    @DisplayName("Moolre 4xx marks transaction FAILED and returns 422")
    void moolre_4xx_marks_failed_and_returns_422() {
        stubActiveAccount();
        when(moolre.initiatePayment(any(), any(), any(), any(), any()))
                .thenThrow(new MoolreClientException("Invalid phone", 400));

        assertThatThrownBy(() -> service.initiateDeposit(momoRequest(10_000L), IDEM_KEY))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(UNPROCESSABLE_ENTITY));

        ArgumentCaptor<TransactionEntity> captor = ArgumentCaptor.forClass(TransactionEntity.class);
        verify(txnRepo, atLeastOnce()).save(captor.capture());
        boolean anyFailed = captor.getAllValues().stream()
                .anyMatch(t -> "FAILED".equals(t.getStatus()));
        assertThat(anyFailed).isTrue();
    }

    @Test
    @DisplayName("idempotency key stored on transaction for audit")
    void idempotency_key_stored_on_transaction() {
        stubActiveAccount();
        stubMoolreSuccess();

        service.initiateDeposit(momoRequest(10_000L), IDEM_KEY);

        ArgumentCaptor<TransactionEntity> captor = ArgumentCaptor.forClass(TransactionEntity.class);
        verify(txnRepo, atLeastOnce()).save(captor.capture());
        assertThat(captor.getAllValues().get(0).getIdempotencyKey()).isEqualTo(IDEM_KEY);
        assertThat(captor.getAllValues().get(0).getExternalProvider()).isEqualTo("MOOLRE");
    }

    @Test
    @DisplayName("destination_ledger_account_id stored on transaction")
    void destination_ledger_account_id_stored_on_transaction() {
        stubActiveAccount();
        stubMoolreSuccess();

        service.initiateDeposit(momoRequest(10_000L), IDEM_KEY);

        ArgumentCaptor<TransactionEntity> captor = ArgumentCaptor.forClass(TransactionEntity.class);
        verify(txnRepo, atLeastOnce()).save(captor.capture());
        assertThat(captor.getAllValues().get(0).getDestinationLedgerAccountId())
                .isEqualTo(ACCOUNT_ID);
    }

    private void stubActiveAccount() {
        when(ledgerRepo.findById(ACCOUNT_ID))
                .thenReturn(Optional.of(activeAccount()));
    }

    private void stubMoolreSuccess() {
        when(moolre.initiatePayment(any(), any(), any(), any(), any()))
                .thenReturn(new PaymentInitiateResult("TR099", null, "session-001", true));
    }

    private LedgerAccountEntity activeAccount() {
        return new LedgerAccountEntity("USER_WALLET", "USER", USER_ID, "desc",
                Instant.now(FIXED_CLOCK));
    }

    private LedgerAccountEntity closedAccount() {
        var acc = new LedgerAccountEntity("USER_WALLET", "USER", USER_ID, "desc",
                Instant.now(FIXED_CLOCK));
        try {
            var f = LedgerAccountEntity.class.getDeclaredField("status");
            f.setAccessible(true);
            f.set(acc, "CLOSED");
        } catch (Exception e) { throw new RuntimeException(e); }
        return acc;
    }

    private LedgerAccountEntity accountOwnedBy(UUID ownerId) {
        return new LedgerAccountEntity("USER_WALLET", "USER", ownerId, "desc",
                Instant.now(FIXED_CLOCK));
    }

    private DepositInitiateRequest momoRequest(long amount) {
        return new DepositInitiateRequest(
                ACCOUNT_ID, USER_ID, amount,
                "MOMO", "akua@stash.test",
                "0241234567", "mtn", "corr-001",
                UUID.randomUUID(), "VAULT_DEPOSIT");
    }
}
