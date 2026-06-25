package com.stash.payments.paystack.client;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.stash.payments.paystack.dto.*;
import com.stash.payments.paystack.exception.*;
import org.junit.jupiter.api.*;
import org.springframework.web.reactive.function.client.WebClient;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.*;

class PaystackClientTest {

    private static WireMockServer wireMock;
    private PaystackClient client;

    @BeforeAll
    static void startWireMock() {
        wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMock.start();
    }

    @AfterAll
    static void stopWireMock() {
        wireMock.stop();
    }

    @BeforeEach
    void setUp() {
        wireMock.resetAll();
        client = new PaystackClient(
                WebClient.builder(),
                "http://localhost:" + wireMock.port(),
                "sk_test_testkey12345678901234567890",
                30
        );
    }

    // ── Charge initiation ─────────────────────────────────────────────────

    @Test
    @DisplayName("initiateCharge returns response on 200")
    void initiate_charge_success() {
        wireMock.stubFor(post(urlEqualTo("/charge"))
                .willReturn(okJson("""
                        {"status":true,"message":"Charge attempted",
                         "data":{"reference":"pay_ref_001","status":"send_otp",
                                 "display_text":"Please enter OTP"}}
                        """)));

        var request = new ChargeInitiateRequest(
                "akua@stash.test", 10_000L,
                new ChargeInitiateRequest.MobileMoneyChannel("0241234567", "mtn"),
                "GHS", "ACCT_test001");

        var response = client.initiateCharge(request);

        assertThat(response.status()).isTrue();
        assertThat(response.data().reference()).isEqualTo("pay_ref_001");
    }

    @Test
    @DisplayName("initiateCharge throws PaystackClientException on 400")
    void initiate_charge_400_throws_client_exception() {
        wireMock.stubFor(post(urlEqualTo("/charge"))
                .willReturn(badRequest().withBody("""
                        {"status":false,"message":"Invalid phone number"}
                        """)));

        var request = new ChargeInitiateRequest(
                "akua@stash.test", 10_000L,
                new ChargeInitiateRequest.MobileMoneyChannel("invalid", "mtn"),
                "GHS", "ACCT_test001");

        assertThatThrownBy(() -> client.initiateCharge(request))
                .isInstanceOf(PaystackClientException.class)
                .satisfies(ex -> assertThat(((PaystackClientException) ex).getHttpStatus())
                        .isEqualTo(400));
    }

    @Test
    @DisplayName("initiateCharge throws PaystackServerException on 500 — NOT retried")
    void initiate_charge_500_throws_not_retried() {
        wireMock.stubFor(post(urlEqualTo("/charge"))
                .willReturn(serverError().withBody("{\"status\":false,\"message\":\"Internal error\"}")));

        var request = new ChargeInitiateRequest(
                "akua@stash.test", 10_000L,
                new ChargeInitiateRequest.MobileMoneyChannel("0241234567", "mtn"),
                "GHS", "ACCT_test001");

        assertThatThrownBy(() -> client.initiateCharge(request))
                .isInstanceOf(PaystackServerException.class);

        // Verify called exactly once — no retry on charge initiation
        wireMock.verify(1, postRequestedFor(urlEqualTo("/charge")));
    }

    // ── Transaction verification (idempotent — retried) ───────────────────

    @Test
    @DisplayName("verifyTransaction returns response on 200")
    void verify_transaction_success() {
        wireMock.stubFor(get(urlEqualTo("/transaction/verify/pay_ref_001"))
                .willReturn(okJson("""
                        {"status":true,"message":"Verification successful",
                         "data":{"reference":"pay_ref_001","status":"success",
                                 "amount":10000,"gateway_response":"Approved"}}
                        """)));

        var response = client.verifyTransaction("pay_ref_001");

        assertThat(response.data().status()).isEqualTo("success");
        assertThat(response.data().amount()).isEqualTo(10_000L);
    }

    @Test
    @DisplayName("verifyTransaction retries on 500 and succeeds on third attempt")
    void verify_transaction_retries_on_500_then_succeeds() {
        wireMock.stubFor(get(urlEqualTo("/transaction/verify/pay_ref_002"))
                .inScenario("retry-scenario")
                .whenScenarioStateIs("Started")
                .willReturn(serverError())
                .willSetStateTo("first-fail"));

        wireMock.stubFor(get(urlEqualTo("/transaction/verify/pay_ref_002"))
                .inScenario("retry-scenario")
                .whenScenarioStateIs("first-fail")
                .willReturn(serverError())
                .willSetStateTo("second-fail"));

        wireMock.stubFor(get(urlEqualTo("/transaction/verify/pay_ref_002"))
                .inScenario("retry-scenario")
                .whenScenarioStateIs("second-fail")
                .willReturn(okJson("""
                        {"status":true,"message":"Verification successful",
                         "data":{"reference":"pay_ref_002","status":"success",
                                 "amount":5000,"gateway_response":"Approved"}}
                        """)));

        var response = client.verifyTransaction("pay_ref_002");

        assertThat(response.data().status()).isEqualTo("success");
        wireMock.verify(3, getRequestedFor(urlEqualTo("/transaction/verify/pay_ref_002")));
    }

    // ── Subaccount creation ───────────────────────────────────────────────

    @Test
    @DisplayName("createSubaccount returns subaccount code on success")
    void create_subaccount_success() {
        wireMock.stubFor(post(urlEqualTo("/subaccount"))
                .willReturn(okJson("""
                        {"status":true,"message":"Subaccount created",
                         "data":{"subaccount_code":"ACCT_abc123",
                                 "business_name":"Akua Mensah",
                                 "settlement_bank":"GCB",
                                 "account_number":"1234567890"}}
                        """)));

        var request = new SubaccountCreateRequest(
                "Akua Mensah", "GCB", "1234567890", 0.0, "User wallet");

        var response = client.createSubaccount(request);

        assertThat(response.data().subaccountCode()).isEqualTo("ACCT_abc123");
    }

    // ── Circuit breaker ───────────────────────────────────────────────────

    @Test
    @DisplayName("circuit breaker opens after 5 consecutive failures; subsequent calls fail fast")
    void circuit_breaker_opens_after_failures() {
        wireMock.stubFor(get(anyUrl())
                .willReturn(serverError()));

        // Exhaust the minimum-number-of-calls threshold (5 calls needed to evaluate)
        for (int i = 0; i < 10; i++) {
            try {
                client.verifyTransaction("ref-cb-" + i);
            } catch (PaystackServerException | PaystackCircuitOpenException ignored) {}
        }

        // Now the circuit should be open — next call fails fast
        assertThatThrownBy(() -> client.verifyTransaction("ref-after-open"))
                .isInstanceOf(PaystackCircuitOpenException.class);

        // WireMock should NOT have received the last call (failed fast)
        wireMock.verify(lessThan(12), getRequestedFor(anyUrl()));
    }

    // ── API key redaction ─────────────────────────────────────────────────

    @Test
    @DisplayName("API key never appears in any log output")
    void api_key_never_logged() {
        // Capture log output via a test appender and assert the key is absent.
        // Using Logback's ListAppender pattern:
        ch.qos.logback.classic.Logger rootLogger =
                (ch.qos.logback.classic.Logger)
                org.slf4j.LoggerFactory.getLogger(PaystackClient.class);
        ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>
                listAppender = new ch.qos.logback.core.read.ListAppender<>();
        listAppender.start();
        rootLogger.addAppender(listAppender);
        rootLogger.setLevel(ch.qos.logback.classic.Level.DEBUG);

        wireMock.stubFor(get(anyUrl()).willReturn(okJson(
                "{\"status\":true,\"message\":\"ok\",\"data\":{}}")));
        try {
            client.queryBalance();
        } catch (Exception ignored) {}

        String allLogs = listAppender.list.stream()
                .map(e -> e.getFormattedMessage())
                .reduce("", String::concat);

        assertThat(allLogs).doesNotContain("sk_test_testkey12345678901234567890");

        rootLogger.detachAppender(listAppender);
    }
}
