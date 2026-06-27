package com.stash.payments.paystack.client;

import com.stash.payments.paystack.dto.*;
import com.stash.payments.paystack.exception.*;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

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
 * <p>Resilience4j is applied <em>programmatically</em> rather than via the
 * {@code @CircuitBreaker}/{@code @Retry} annotations so that the behaviour is
 * exercised even when the client is constructed directly (e.g. in unit tests),
 * not only when it is a Spring-managed proxy. When created by Spring the shared
 * registries (configured from {@code application.yml}) are used so metrics and
 * health indicators reflect real traffic; the convenience constructor builds
 * primitives with the same configuration for tests.
 *
 * <p><strong>Logging:</strong> all calls logged at DEBUG. The API key is set on
 * the Authorization header and never interpolated into log messages. Response
 * bodies are summarised — no raw body logging.
 */
@Component
public class PaystackClient {

    private static final Logger log = LoggerFactory.getLogger(PaystackClient.class);

    static final String CIRCUIT_BREAKER_NAME = "paystack";
    static final String RETRY_NAME = "paystack-idempotent";

    private final WebClient webClient;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;
    private final Duration timeout;

    /**
     * Spring constructor — uses the shared, {@code application.yml}-configured
     * Resilience4j registries.
     */
    @Autowired
    public PaystackClient(
            WebClient.Builder webClientBuilder,
            @Value("${paystack.base-url}") String baseUrl,
            @Value("${paystack.secret-key}") String secretKey,
            @Value("${paystack.timeout-seconds:30}") int timeoutSeconds,
            CircuitBreakerRegistry circuitBreakerRegistry,
            RetryRegistry retryRegistry) {

        this(webClientBuilder, baseUrl, secretKey, timeoutSeconds,
                circuitBreakerRegistry.circuitBreaker(CIRCUIT_BREAKER_NAME),
                retryRegistry.retry(RETRY_NAME));
    }

    /**
     * Convenience constructor — builds Resilience4j primitives with the same
     * configuration as {@code application.yml}. Used in tests where there is no
     * Spring context.
     */
    public PaystackClient(
            WebClient.Builder webClientBuilder,
            String baseUrl,
            String secretKey,
            int timeoutSeconds) {

        this(webClientBuilder, baseUrl, secretKey, timeoutSeconds,
                defaultCircuitBreaker(), defaultRetry());
    }

    private PaystackClient(
            WebClient.Builder webClientBuilder,
            String baseUrl,
            String secretKey,
            int timeoutSeconds,
            CircuitBreaker circuitBreaker,
            Retry retry) {

        validateKeyFormat(secretKey);

        this.circuitBreaker = circuitBreaker;
        this.retry = retry;
        this.timeout = Duration.ofSeconds(timeoutSeconds);

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

    private static CircuitBreaker defaultCircuitBreaker() {
        return CircuitBreaker.of(CIRCUIT_BREAKER_NAME, CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(10)
                .failureRateThreshold(50)
                .minimumNumberOfCalls(5)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .permittedNumberOfCallsInHalfOpenState(3)
                .build());
    }

    private static Retry defaultRetry() {
        return Retry.of(RETRY_NAME, RetryConfig.custom()
                .maxAttempts(3)
                .intervalFunction(IntervalFunction.ofExponentialBackoff(500L, 2.0))
                .retryExceptions(PaystackServerException.class, TimeoutException.class)
                .build());
    }

    // ── Charge initiation — NOT retried (non-idempotent) ──────────────────

    /**
     * Initiates a mobile money charge. Returns immediately with a reference;
     * the actual confirmation arrives via Paystack webhook.
     *
     * <p>Guarded by the circuit breaker but NOT retried — retrying a charge
     * initiation on timeout risks double-charging the user.
     */
    public ChargeInitiateResponse initiateCharge(ChargeInitiateRequest request) {
        log.debug("Paystack.initiateCharge: email=REDACTED amount={}p", request.amount());

        return callWithCircuitBreaker(() -> post("/charge", request, ChargeInitiateResponse.class));
    }

    // ── Idempotent calls — retried on 5xx and timeout ─────────────────────

    public TransactionVerifyResponse verifyTransaction(String reference) {
        log.debug("Paystack.verifyTransaction: reference={}", reference);

        return callIdempotent(() -> get("/transaction/verify/" + reference, TransactionVerifyResponse.class));
    }

    public SubaccountCreateResponse createSubaccount(SubaccountCreateRequest request) {
        log.debug("Paystack.createSubaccount: businessName=REDACTED bank={}",
                request.settlementBank());

        return callIdempotent(() -> post("/subaccount", request, SubaccountCreateResponse.class));
    }

    public TransferRecipientCreateResponse createTransferRecipient(
            TransferRecipientCreateRequest request) {
        log.debug("Paystack.createTransferRecipient: type={} bank={}",
                request.type(), request.bankCode());

        return callIdempotent(() ->
                post("/transferrecipient", request, TransferRecipientCreateResponse.class));
    }

    public TransferInitiateResponse initiateTransfer(TransferInitiateRequest request) {
        log.debug("Paystack.initiateTransfer: reference={} amount={}p",
                request.reference(), request.amount());

        return callIdempotent(() -> post("/transfer", request, TransferInitiateResponse.class));
    }

    public Object queryBalance() {
        log.debug("Paystack.queryBalance");
        return callIdempotent(() -> get("/balance", Object.class));
    }

    // ── Resilience wrappers ───────────────────────────────────────────────

    /** Circuit breaker only — for non-idempotent calls. */
    private <T> T callWithCircuitBreaker(Supplier<T> action) {
        try {
            return circuitBreaker.executeSupplier(action);
        } catch (CallNotPermittedException ex) {
            log.warn("Paystack circuit breaker is OPEN — failing fast");
            throw new PaystackCircuitOpenException();
        }
    }

    /** Retry (outer) wrapping the circuit breaker (inner) — for idempotent calls. */
    private <T> T callIdempotent(Supplier<T> action) {
        Supplier<T> guarded = CircuitBreaker.decorateSupplier(circuitBreaker, action);
        Supplier<T> retrying = Retry.decorateSupplier(retry, guarded);
        try {
            return retrying.get();
        } catch (CallNotPermittedException ex) {
            log.warn("Paystack circuit breaker is OPEN — failing fast");
            throw new PaystackCircuitOpenException();
        }
    }

    // ── HTTP helpers ──────────────────────────────────────────────────────

    private <T> T post(String path, Object body, Class<T> responseType) {
        return webClient.post()
                .uri(path)
                .bodyValue(body)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, this::toClientException)
                .onStatus(HttpStatusCode::is5xxServerError, this::toServerException)
                .bodyToMono(responseType)
                .timeout(timeout)
                .block();
    }

    private <T> T get(String path, Class<T> responseType) {
        return webClient.get()
                .uri(path)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, this::toClientException)
                .onStatus(HttpStatusCode::is5xxServerError, this::toServerException)
                .bodyToMono(responseType)
                .timeout(timeout)
                .block();
    }

    // Map error responses to typed exceptions. defaultIfEmpty ensures the
    // exception is still raised when Paystack returns an empty error body.
    private Mono<? extends Throwable> toClientException(ClientResponse response) {
        int status = response.statusCode().value();
        return response.bodyToMono(String.class).defaultIfEmpty("")
                .map(body -> new PaystackClientException(summarise(body), status));
    }

    private Mono<? extends Throwable> toServerException(ClientResponse response) {
        int status = response.statusCode().value();
        return response.bodyToMono(String.class).defaultIfEmpty("")
                .map(body -> new PaystackServerException(summarise(body), status));
    }

    // ── Validation and utilities ──────────────────────────────────────────

    private void validateKeyFormat(String key) {
        if (key == null || (!key.startsWith("sk_test_") && !key.startsWith("sk_live_"))) {
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
