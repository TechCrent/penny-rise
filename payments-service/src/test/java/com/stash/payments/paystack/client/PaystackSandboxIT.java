package com.stash.payments.paystack.client;

import com.stash.payments.paystack.dto.ChargeInitiateRequest;
import com.stash.payments.paystack.dto.SubaccountCreateRequest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.reactive.function.client.WebClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test against the real Paystack sandbox.
 * Excluded from normal CI — run manually with:
 *   PAYSTACK_SECRET_KEY=sk_test_... mvn test -Dgroups=paystack-sandbox
 */
@Tag("paystack-sandbox")
@EnabledIfEnvironmentVariable(named = "PAYSTACK_SECRET_KEY", matches = "sk_test_.*")
class PaystackSandboxIT {

    private final PaystackClient client = new PaystackClient(
            WebClient.builder(),
            "https://api.paystack.co",
            System.getenv("PAYSTACK_SECRET_KEY"),
            30
    );

    @Test
    @Tag("paystack-sandbox")
    void sandbox_charge_initiation_returns_reference() {
        var request = new ChargeInitiateRequest(
                "sandbox@stash.test",
                10_000L,
                new ChargeInitiateRequest.MobileMoneyChannel("0241234567", "mtn"),
                "GHS",
                null   // no subaccount for this smoke test
        );

        var response = client.initiateCharge(request);

        assertThat(response.status()).isTrue();
        assertThat(response.data().reference()).isNotBlank();
        System.out.println("Sandbox charge reference: " + response.data().reference());
    }

    @Test
    @Tag("paystack-sandbox")
    void sandbox_subaccount_creation_returns_code() {
        var request = new SubaccountCreateRequest(
                "Stash Test User " + System.currentTimeMillis(),
                "TEST",         // Paystack test bank code
                "0000000000",   // Paystack test account number
                0.0,
                "Sandbox smoke test"
        );

        var response = client.createSubaccount(request);

        assertThat(response.status()).isTrue();
        assertThat(response.data().subaccountCode()).startsWith("ACCT_");
        System.out.println("Sandbox subaccount code: " + response.data().subaccountCode());
    }
}
