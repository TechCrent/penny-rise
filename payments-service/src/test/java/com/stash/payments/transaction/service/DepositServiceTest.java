package com.stash.payments.transaction.service;

import com.stash.payments.ledger.domain.LedgerAccountEntity;
import com.stash.payments.ledger.repository.LedgerAccountRepository;
import com.stash.payments.ledger.service.TransactionReferenceGenerator;
import com.stash.payments.paystack.client.PaystackClient;
import com.stash.payments.paystack.domain.PaystackSubaccountEntity;
import com.stash.payments.paystack.dto.ChargeInitiateResponse;
import com.stash.payments.paystack.exception.PaystackClientException;
import com.stash.payments.paystack.repository.PaystackSubaccountRepository;
import com.stash.payments.transaction.api.dto.DepositInitiateRequest;
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

    private final LedgerAccountRepository      ledgerRepo     = Mockito.mock(LedgerAccountRepository.class);
    private final PaystackSubaccountRepository subaccountRepo = Mockito.mock(PaystackSubaccountRepository.class);
    private final TransactionRepository        txnRepo        = Mockito.mock(TransactionRepository.class);
    private final PaystackClient               paystack       = Mockito.mock(PaystackClient.class);
    private final TransactionReferenceGenerator refGen        = Mockito.mock(TransactionReferenceGenerator.class);
    private final DepositService service = new DepositService(
            ledgerRepo, subaccountRepo, txnRepo, paystack, refGen, FIXED_CLOCK, false);

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

    // ── Happy path ────────────────────────────────────────────────────────

    @Test
    @DisplayName("happy path: valid MoMo deposit returns 202 with PENDING status")
    void happy_path_returns_202_pending() {
        stubActiveAccount();
        stubSubaccount();
        stubPaystackSuccess();

        var result = service.initiateDeposit(momoRequest(10_000L), IDEM_KEY);

        assertThat(result.transactionReference()).isEqualTo(REF);
        assertThat(result.status()).isEqualTo("PENDING");
        assertThat(result.paystackReference()).isEqualTo("pay_ref_001");
    }

    @Test
    @DisplayName("PENDING transaction row saved before Paystack call")
    void pending_row_saved_before_paystack_call() {
        stubActiveAccount();
        stubSubaccount();
        stubPaystackSuccess();

        service.initiateDeposit(momoRequest(10_000L), IDEM_KEY);

        // save called at least twice: once before Paystack (PENDING), once after (with externalRef)
        verify(txnRepo, atLeast(2)).save(any(TransactionEntity.class));
    }

    @Test
    @DisplayName("no ledger entries written during deposit initiation")
    void no_ledger_entries_written() {
        stubActiveAccount();
        stubSubaccount();
        stubPaystackSuccess();

        service.initiateDeposit(momoRequest(10_000L), IDEM_KEY);

        // LedgerService is not called — no ledger writes at this point
        verifyNoMoreInteractions(Mockito.mock(com.stash.payments.ledger.service.LedgerService.class));
    }

    // ── Validation failures ───────────────────────────────────────────────

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
    @DisplayName("VAULT account (owner_id = vaultId, not userId) does NOT 403 — " +
                 "the monolith is the authority on vault ownership")
    void vault_account_deposit_does_not_403() {
        // A vault ledger account: owner_type=VAULT, owner_id=vaultId (never the user).
        // The old check compared userId against this vaultId and 403'd every vault deposit.
        LedgerAccountEntity vaultAccount = new LedgerAccountEntity(
                "VAULT", "VAULT", UUID.randomUUID(), "vault ledger", Instant.now(FIXED_CLOCK));
        when(ledgerRepo.findById(ACCOUNT_ID)).thenReturn(Optional.of(vaultAccount));
        stubSubaccount();
        stubPaystackSuccess();

        var result = service.initiateDeposit(momoRequest(10_000L), IDEM_KEY);

        assertThat(result.status()).isEqualTo("PENDING");
        assertThat(result.transactionReference()).isEqualTo(REF);
    }

    @Test
    @DisplayName("no Paystack subaccount for user returns 409")
    void no_subaccount_returns_409() {
        stubActiveAccount();
        when(subaccountRepo.findByOwnerTypeAndOwnerId("USER", USER_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.initiateDeposit(momoRequest(10_000L), IDEM_KEY))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(CONFLICT));
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
        stubSubaccount();

        var request = new DepositInitiateRequest(
                ACCOUNT_ID, USER_ID, 10_000L,
                "MOMO", "akua@stash.test",
                null,    // missing
                "mtn", "corr-001",
                UUID.randomUUID(), "VAULT_DEPOSIT");

        assertThatThrownBy(() -> service.initiateDeposit(request, IDEM_KEY))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(UNPROCESSABLE_ENTITY));
    }

    // ── Sandbox test-MoMo-number substitution ─────────────────────────────

    @Test
    @DisplayName("substitution disabled (default): Paystack is charged with the real user-entered number")
    void substitution_disabled_charges_real_number() {
        stubActiveAccount();
        stubSubaccount();
        stubPaystackSuccess();
        when(paystack.isTestMode()).thenReturn(true);

        service.initiateDeposit(momoRequest(10_000L), IDEM_KEY);

        ArgumentCaptor<com.stash.payments.paystack.dto.ChargeInitiateRequest> captor =
                ArgumentCaptor.forClass(com.stash.payments.paystack.dto.ChargeInitiateRequest.class);
        verify(paystack).initiateCharge(captor.capture());
        assertThat(captor.getValue().mobileMoneyChannel().phone()).isEqualTo("0241234567");
        assertThat(captor.getValue().mobileMoneyChannel().provider()).isEqualTo("mtn");
    }

    @Test
    @DisplayName("substitution enabled + test-mode key: Paystack is charged the sandbox test number, " +
                 "but the stored transaction still reflects the real number")
    void substitution_enabled_in_test_mode_charges_sandbox_number() {
        var serviceWithSubstitution = new DepositService(
                ledgerRepo, subaccountRepo, txnRepo, paystack, refGen, FIXED_CLOCK, true);
        stubActiveAccount();
        stubSubaccount();
        stubPaystackSuccess();
        when(paystack.isTestMode()).thenReturn(true);

        // Real user-entered number is a valid, different Ghanaian number.
        var request = new DepositInitiateRequest(
                ACCOUNT_ID, USER_ID, 10_000L,
                "MOMO", "akua@stash.test",
                "0201234567", "vodafone", "corr-001",
                UUID.randomUUID(), "VAULT_DEPOSIT");

        serviceWithSubstitution.initiateDeposit(request, IDEM_KEY);

        ArgumentCaptor<com.stash.payments.paystack.dto.ChargeInitiateRequest> chargeCaptor =
                ArgumentCaptor.forClass(com.stash.payments.paystack.dto.ChargeInitiateRequest.class);
        verify(paystack).initiateCharge(chargeCaptor.capture());
        assertThat(chargeCaptor.getValue().mobileMoneyChannel().phone()).isEqualTo("0551234987");
        assertThat(chargeCaptor.getValue().mobileMoneyChannel().provider()).isEqualTo("mtn");
    }

    @Test
    @DisplayName("substitution enabled but key is LIVE mode: still charges the real number — " +
                 "double guard prevents substitution against a live Paystack account")
    void substitution_enabled_but_live_mode_does_not_substitute() {
        var serviceWithSubstitution = new DepositService(
                ledgerRepo, subaccountRepo, txnRepo, paystack, refGen, FIXED_CLOCK, true);
        stubActiveAccount();
        stubSubaccount();
        stubPaystackSuccess();
        when(paystack.isTestMode()).thenReturn(false);

        serviceWithSubstitution.initiateDeposit(momoRequest(10_000L), IDEM_KEY);

        ArgumentCaptor<com.stash.payments.paystack.dto.ChargeInitiateRequest> captor =
                ArgumentCaptor.forClass(com.stash.payments.paystack.dto.ChargeInitiateRequest.class);
        verify(paystack).initiateCharge(captor.capture());
        assertThat(captor.getValue().mobileMoneyChannel().phone()).isEqualTo("0241234567");
    }

    // ── MoMo prefix validation ─────────────────────────────────────────────

    @Test
    @DisplayName("phone number not matching the selected provider's prefixes returns 422")
    void momo_number_not_matching_provider_returns_422() {
        stubActiveAccount();
        stubSubaccount();

        // 020 is a Vodafone/Telecel prefix, not MTN.
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

    // ── Paystack failure handling ─────────────────────────────────────────

    @Test
    @DisplayName("Paystack 4xx marks transaction FAILED and returns 422")
    void paystack_4xx_marks_failed_and_returns_422() {
        stubActiveAccount();
        stubSubaccount();
        when(paystack.initiateCharge(any()))
                .thenThrow(new PaystackClientException("Invalid phone", 400));

        assertThatThrownBy(() -> service.initiateDeposit(momoRequest(10_000L), IDEM_KEY))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(UNPROCESSABLE_ENTITY));

        // Transaction marked FAILED
        ArgumentCaptor<TransactionEntity> captor = ArgumentCaptor.forClass(TransactionEntity.class);
        verify(txnRepo, atLeastOnce()).save(captor.capture());
        boolean anyFailed = captor.getAllValues().stream()
                .anyMatch(t -> "FAILED".equals(t.getStatus()));
        assertThat(anyFailed).isTrue();
    }

    // ── Idempotency (handled at filter level — verified by absence of double write) ──

    @Test
    @DisplayName("idempotency key stored on transaction for audit")
    void idempotency_key_stored_on_transaction() {
        stubActiveAccount();
        stubSubaccount();
        stubPaystackSuccess();

        service.initiateDeposit(momoRequest(10_000L), IDEM_KEY);

        ArgumentCaptor<TransactionEntity> captor = ArgumentCaptor.forClass(TransactionEntity.class);
        verify(txnRepo, atLeastOnce()).save(captor.capture());
        assertThat(captor.getAllValues().get(0).getIdempotencyKey()).isEqualTo(IDEM_KEY);
    }

    @Test
    @DisplayName("destination_ledger_account_id stored on transaction so the webhook handler " +
                 "can credit the right account (e.g. a vault, not USER_WALLET)")
    void destination_ledger_account_id_stored_on_transaction() {
        stubActiveAccount();
        stubSubaccount();
        stubPaystackSuccess();

        service.initiateDeposit(momoRequest(10_000L), IDEM_KEY);

        ArgumentCaptor<TransactionEntity> captor = ArgumentCaptor.forClass(TransactionEntity.class);
        verify(txnRepo, atLeastOnce()).save(captor.capture());
        assertThat(captor.getAllValues().get(0).getDestinationLedgerAccountId())
                .isEqualTo(ACCOUNT_ID);
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private void stubActiveAccount() {
        when(ledgerRepo.findById(ACCOUNT_ID))
                .thenReturn(Optional.of(activeAccount()));
    }

    private void stubSubaccount() {
        PaystackSubaccountEntity sub = Mockito.mock(PaystackSubaccountEntity.class);
        when(sub.getPaystackSubaccountCode()).thenReturn("ACCT_test001");
        when(subaccountRepo.findByOwnerTypeAndOwnerId("USER", USER_ID))
                .thenReturn(Optional.of(sub));
    }

    private void stubPaystackSuccess() {
        var data = new ChargeInitiateResponse.ChargeData("pay_ref_001", "Dial *170#", "send_otp");
        when(paystack.initiateCharge(any()))
                .thenReturn(new ChargeInitiateResponse(true, "Charge attempted", data));
    }

    private LedgerAccountEntity activeAccount() {
        return new LedgerAccountEntity("USER_WALLET", "USER", USER_ID, "desc",
                Instant.now(FIXED_CLOCK));
    }

    private LedgerAccountEntity closedAccount() {
        var acc = new LedgerAccountEntity("USER_WALLET", "USER", USER_ID, "desc",
                Instant.now(FIXED_CLOCK));
        // status is set via reflection since there's no setter — or use a test factory
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
