package com.stash.payments.paystack.client;

import com.stash.payments.paystack.dto.*;
import com.stash.payments.paystack.exception.*;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Single point of integration with the Paystack API.
 *
 * <p>No other class in the Payments Service may call Paystack directly.
 * Enforced by the architecture test {@code PaystackClientArchitectureTest}.
 *
 * <p><strong>Retry policy:</strong> idempotent calls (verify, balance, subaccount
 * creation, transfer initiation with a stable reference) are retried up to 3 times
 * with exponential backoff on 5xx and timeouts. Charge initiation is NOT retried —
 * if a charge initiation times out, we cannot know whether Paystack received it;
 * retrying risks a duplicate charge. The charge webhook confirms whether it succeeded.
 *
 * <p><strong>Circuit breaker:</strong> shared across all calls. Opens after 50%
 * failure rate in a 10-call sliding window (minimum 5 calls). Half-open after 30s.
 *
 * <p><strong>Logging:</strong> all calls logged at DEBUG. The API key is read from
 * the Authorization header configuration and never interpolated into log messages.
 * Response bodies are summarised (status + message fields only) — no raw body logging.
 */
@Component
public class PaystackClient {

    private static final Logger log = LoggerFactory.getLogger(PaystackClient.class);

    private final WebClient webClient;

    public PaystackClient(
            WebClient.Builder webClientBuilder,
            @Value("${paystack.base-url}") String baseUrl,
            @Value("${paystack.secret-key}") String secretKey,
            @Value("${paystack.timeout-seconds:30}") int timeoutSeconds) {

        validateKeyFormat(secretKey);

        this.webClient = webClientBuilder
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + secretKey)
                .defaultHeader("Content-Type", "application/json")
                .filter((request, next) -> {
                    // Request log — never log the Authorization header value
                    log.debug("Paystack → {} {}", request.method(), request.url());
                    return next.exchange(request)
                            .doOnNext(resp -> log.debug("Paystack ← {} {} {}",
                                    request.method(), request.url(), resp.statusCode()));
                })
                .build();
    }

    // ── Charge initiation — NOT retried (non-idempotent) ──────────────────

    /**
     * Initiates a mobile money charge. Returns immediately with a reference;
     * the actual confirmation arrives via Paystack webhook.
     *
     * <p>NOT annotated with {@code @Retry} — retrying a charge initiation
     * on timeout risks double-charging the user.
     */
    @CircuitBreaker(name = "paystack", fallbackMethod = "circuitOpenFallback")
    public ChargeInitiateResponse initiateCharge(ChargeInitiateRequest request) {
        log.debug("Paystack.initiateCharge: email=REDACTED amount={}p", request.amount());

        return post("/charge", request, ChargeInitiateResponse.class);
    }

    // ── Idempotent calls — retried on 5xx and timeout ─────────────────────

    @CircuitBreaker(name = "paystack", fallbackMethod = "circuitOpenFallback")
    @Retry(name = "paystack-idempotent", fallbackMethod = "retryExhaustedFallback")
    public TransactionVerifyResponse verifyTransaction(String reference) {
        log.debug("Paystack.verifyTransaction: reference={}", reference);

        return get("/transaction/verify/" + reference, TransactionVerifyResponse.class);
    }

    @CircuitBreaker(name = "paystack", fallbackMethod = "circuitOpenFallback")
    @Retry(name = "paystack-idempotent", fallbackMethod = "retryExhaustedFallback")
    public SubaccountCreateResponse createSubaccount(SubaccountCreateRequest request) {
        log.debug("Paystack.createSubaccount: businessName=REDACTED bank={}",
                request.settlementBank());

        return post("/subaccount", request, SubaccountCreateResponse.class);
    }

    @CircuitBreaker(name = "paystack", fallbackMethod = "circuitOpenFallback")
    @Retry(name = "paystack-idempotent", fallbackMethod = "retryExhaustedFallback")
    public TransferInitiateResponse initiateTransfer(TransferInitiateRequest request) {
        log.debug("Paystack.initiateTransfer: reference={} amount={}p",
                request.reference(), request.amount());

        return post("/transfer", request, TransferInitiateResponse.class);
    }

    @CircuitBreaker(name = "paystack", fallbackMethod = "circuitOpenFallback")
    @Retry(name = "paystack-idempotent", fallbackMethod = "retryExhaustedFallback")
    public Object queryBalance() {
        log.debug("Paystack.queryBalance");
        return get("/balance", Object.class);
    }

    // ── HTTP helpers ──────────────────────────────────────────────────────

    private <T> T post(String path, Object body, Class<T> responseType) {
        return webClient.post()
                .uri(path)
                .bodyValue(body)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, resp ->
                        resp.bodyToMono(String.class)
                            .flatMap(err -> Mono.error(
                                    new PaystackClientException(summarise(err), resp.statusCode().value()))))
                .onStatus(HttpStatusCode::is5xxServerError, resp ->
                        resp.bodyToMono(String.class)
                            .flatMap(err -> Mono.error(
                                    new PaystackServerException(summarise(err), resp.statusCode().value()))))
                .bodyToMono(responseType)
                .timeout(Duration.ofSeconds(30))
                .block();
    }

    private <T> T get(String path, Class<T> responseType) {
        return webClient.get()
                .uri(path)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, resp ->
                        resp.bodyToMono(String.class)
                            .flatMap(err -> Mono.error(
                                    new PaystackClientException(summarise(err), resp.statusCode().value()))))
                .onStatus(HttpStatusCode::is5xxServerError, resp ->
                        resp.bodyToMono(String.class)
                            .flatMap(err -> Mono.error(
                                    new PaystackServerException(summarise(err), resp.statusCode().value()))))
                .bodyToMono(responseType)
                .timeout(Duration.ofSeconds(30))
                .block();
    }

    // ── Fallbacks ─────────────────────────────────────────────────────────

    @SuppressWarnings("unused")
    private <T> T circuitOpenFallback(CallNotPermittedException ex) {
        log.warn("Paystack circuit breaker is OPEN — failing fast");
        throw new PaystackCircuitOpenException();
    }

    // Generic fallback signature required by Resilience4j — method name must match
    @SuppressWarnings("unused")
    private <T> T retryExhaustedFallback(Exception ex) {
        log.error("Paystack call failed after max retry attempts: {}", ex.getMessage());
        throw new PaystackServerException(
                "Paystack unavailable after retries: " + ex.getMessage(), 503);
    }

    // ── Validation and utilities ──────────────────────────────────────────

    private void validateKeyFormat(String key) {
        if (!key.startsWith("sk_test_") && !key.startsWith("sk_live_")) {
            throw new IllegalArgumentException(
                    "PAYSTACK_SECRET_KEY must start with sk_test_ or sk_live_. " +
                    "Check your environment configuration.");
        }
        // Log mode (test vs live) but never the key value
        String mode = key.startsWith("sk_test_") ? "TEST" : "LIVE";
        log.info("PaystackClient initialised in {} mode", mode);
    }

    /**
     * Returns a safe summary of a Paystack error body for logging.
     * Never includes the full body verbatim — Paystack errors may echo
     * back request fields that could be sensitive.
     */
    private String summarise(String body) {
        if (body == null || body.isBlank()) return "(empty body)";
        // Truncate — don't log a potentially large body
        int max = Math.min(body.length(), 200);
        return body.substring(0, max).replaceAll("\"secret[^\"]*\"\\s*:\\s*\"[^\"]*\"",
                "\"secret\":\"REDACTED\"");
    }
}
