package com.stash.payments.moolre.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stash.payments.moolre.dto.*;
import com.stash.payments.moolre.exception.*;
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
 * Single point of integration with the Moolre API.
 *
 * <p><strong>Retry policy:</strong> idempotent calls (validate, transfer,
 * status query) are retried up to 3 times with exponential backoff on 5xx
 * and timeouts. Payment initiation is NOT retried — OTP / USSD edge cases
 * make a blind retry unsafe even though {@code externalref} provides partial
 * idempotency.
 *
 * <p><strong>Auth:</strong> always sends {@code X-API-USER}. In live mode,
 * private ops use {@code X-API-KEY}; public status queries use
 * {@code X-API-PUBKEY}. Sandbox only requires {@code X-API-USER}.
 *
 * <p>Resilience4j is applied programmatically (same pattern as
 * {@code PaystackClient}) so behaviour works in unit tests without Spring AOP.
 */
@Component
public class MoolreClient {

    private static final Logger log = LoggerFactory.getLogger(MoolreClient.class);

    static final String CIRCUIT_BREAKER_NAME = "moolre";
    static final String RETRY_NAME = "moolre-idempotent";

    private final WebClient webClient;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;
    private final Duration timeout;
    private final ObjectMapper objectMapper;
    private final boolean sandbox;
    private final String apiKey;
    private final String apiPubkey;
    private final String accountNumber;

    @Autowired
    public MoolreClient(
            WebClient.Builder webClientBuilder,
            ObjectMapper objectMapper,
            @Value("${moolre.base-url}") String baseUrl,
            @Value("${moolre.api-user}") String apiUser,
            @Value("${moolre.api-key:}") String apiKey,
            @Value("${moolre.api-pubkey:}") String apiPubkey,
            @Value("${moolre.account-number:}") String accountNumber,
            @Value("${moolre.timeout-seconds:30}") int timeoutSeconds,
            @Value("${moolre.sandbox:true}") boolean sandbox,
            CircuitBreakerRegistry circuitBreakerRegistry,
            RetryRegistry retryRegistry) {

        this(webClientBuilder, objectMapper, baseUrl, apiUser, apiKey, apiPubkey,
                accountNumber, timeoutSeconds, sandbox,
                circuitBreakerRegistry.circuitBreaker(CIRCUIT_BREAKER_NAME),
                retryRegistry.retry(RETRY_NAME));
    }

    /** Convenience constructor for unit tests (no Spring registries). */
    public MoolreClient(
            WebClient.Builder webClientBuilder,
            ObjectMapper objectMapper,
            String baseUrl,
            String apiUser,
            String apiKey,
            String apiPubkey,
            String accountNumber,
            int timeoutSeconds,
            boolean sandbox) {

        this(webClientBuilder, objectMapper, baseUrl, apiUser, apiKey, apiPubkey,
                accountNumber, timeoutSeconds, sandbox,
                defaultCircuitBreaker(),
                defaultRetry());
    }

    private MoolreClient(
            WebClient.Builder webClientBuilder,
            ObjectMapper objectMapper,
            String baseUrl,
            String apiUser,
            String apiKey,
            String apiPubkey,
            String accountNumber,
            int timeoutSeconds,
            boolean sandbox,
            CircuitBreaker circuitBreaker,
            Retry retry) {

        if (apiUser == null || apiUser.isBlank()) {
            if (!sandbox) {
                throw new IllegalArgumentException(
                        "MOOLRE_API_USER must be set in live mode. Check your environment configuration.");
            }
            log.warn("MOOLRE_API_USER is blank — Moolre calls will fail until it is configured");
        }

        this.objectMapper = objectMapper;
        this.apiKey = apiKey == null ? "" : apiKey;
        this.apiPubkey = apiPubkey == null ? "" : apiPubkey;
        this.accountNumber = accountNumber == null ? "" : accountNumber;
        this.sandbox = sandbox;
        this.circuitBreaker = circuitBreaker;
        this.retry = retry;
        this.timeout = Duration.ofSeconds(timeoutSeconds);

        // Accept JSON explicitly. Moolre sandbox often still responds with
        // Content-Type: text/html;charset=UTF-8 even for JSON bodies — we
        // decode as String then parse (see post()) for that reason.
        WebClient.Builder builder = webClientBuilder
                .baseUrl(baseUrl)
                .defaultHeader("Content-Type", "application/json")
                .defaultHeader("Accept", "application/json");
        if (apiUser != null && !apiUser.isBlank()) {
            builder = builder.defaultHeader("X-API-USER", apiUser);
        }
        this.webClient = builder
                .filter((request, next) -> {
                    log.debug("Moolre → {} {}", request.method(), request.url());
                    return next.exchange(request)
                            .doOnNext(resp -> log.debug("Moolre ← {} {} {}",
                                    request.method(), request.url(), resp.statusCode()));
                })
                .build();

        log.info("MoolreClient initialised in {} mode", sandbox ? "SANDBOX" : "LIVE");
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
                .retryExceptions(MoolreServerException.class, TimeoutException.class)
                .build());
    }

    public boolean isSandbox() {
        return sandbox;
    }

    // ── Payment initiation — NOT retried ──────────────────────────────────

    /**
     * Initiates a MoMo USSD payment collection. Not retried.
     */
    public PaymentInitiateResult initiatePayment(
            String channel,
            String payerInternational,
            String amountGhs,
            String externalRef,
            String reference) {

        log.debug("Moolre.initiatePayment: channel={} amount={} externalRef={}",
                channel, amountGhs, externalRef);

        PaymentInitiateRequest body = PaymentInitiateRequest.of(
                channel, payerInternational, amountGhs, externalRef, reference, accountNumber);

        MoolreEnvelope envelope = callWithCircuitBreaker(
                () -> postPrivate("/open/transact/payment", body));
        return PaymentInitiateResult.from(envelope);
    }

    /**
     * Completes a payment that previously returned OTP-required ({@code TP14}).
     * Not retried.
     */
    public PaymentInitiateResult initiatePaymentWithOtp(
            String channel,
            String payerInternational,
            String amountGhs,
            String externalRef,
            String reference,
            String otpCode) {

        log.debug("Moolre.initiatePaymentWithOtp: channel={} amount={} externalRef={}",
                channel, amountGhs, externalRef);

        PaymentInitiateRequest body = PaymentInitiateRequest.withOtp(
                channel, payerInternational, amountGhs, externalRef, reference,
                otpCode, accountNumber);

        MoolreEnvelope envelope = callWithCircuitBreaker(
                () -> postPrivate("/open/transact/payment", body));
        return PaymentInitiateResult.from(envelope);
    }

    // ── Idempotent calls — retried on 5xx and timeout ─────────────────────

    public String validateRecipient(String channel, String receiverInternational) {
        log.debug("Moolre.validateRecipient: channel={}", channel);

        ValidateRecipientRequest body = ValidateRecipientRequest.of(
                channel, receiverInternational, accountNumber);

        MoolreEnvelope envelope = callIdempotent(
                () -> postPrivate("/open/transact/validate", body));
        return envelope.dataAsText();
    }

    public TransferResult initiateTransfer(
            String channel,
            String receiverInternational,
            String amountGhs,
            String externalRef,
            String reference) {

        log.debug("Moolre.initiateTransfer: channel={} amount={} externalRef={}",
                channel, amountGhs, externalRef);

        TransferInitiateRequest body = TransferInitiateRequest.of(
                channel, receiverInternational, amountGhs, externalRef, reference, accountNumber);

        MoolreEnvelope envelope = callIdempotent(
                () -> postPrivate("/open/transact/transfer", body));
        return TransferResult.from(envelope, objectMapper);
    }

    /**
     * Queries payment/transfer status by {@code externalref}.
     *
     * @param usePublicKey when true (and not sandbox), send {@code X-API-PUBKEY}
     *                     instead of {@code X-API-KEY} — matches Moolre payment-status docs
     */
    public StatusResult queryStatus(String externalRef, boolean usePublicKey) {
        log.debug("Moolre.queryStatus: externalRef={} usePublicKey={}",
                externalRef, usePublicKey);

        StatusQueryRequest body = StatusQueryRequest.byExternalRef(externalRef, accountNumber);

        MoolreEnvelope envelope = callIdempotent(() ->
                usePublicKey
                        ? postPublic("/open/transact/status", body)
                        : postPrivate("/open/transact/status", body));
        return StatusResult.from(envelope, objectMapper);
    }

    // ── Resilience wrappers ───────────────────────────────────────────────

    private <T> T callWithCircuitBreaker(Supplier<T> action) {
        try {
            return circuitBreaker.executeSupplier(action);
        } catch (CallNotPermittedException ex) {
            log.warn("Moolre circuit breaker '{}' is OPEN — failing fast", circuitBreaker.getName());
            throw new MoolreCircuitOpenException();
        }
    }

    private <T> T callIdempotent(Supplier<T> action) {
        Supplier<T> guarded = CircuitBreaker.decorateSupplier(circuitBreaker, action);
        Supplier<T> retrying = Retry.decorateSupplier(retry, guarded);
        try {
            return retrying.get();
        } catch (CallNotPermittedException ex) {
            log.warn("Moolre circuit breaker '{}' is OPEN — failing fast", circuitBreaker.getName());
            throw new MoolreCircuitOpenException();
        }
    }

    // ── HTTP helpers ──────────────────────────────────────────────────────

    private MoolreEnvelope postPrivate(String path, Object body) {
        return post(path, body, false);
    }

    private MoolreEnvelope postPublic(String path, Object body) {
        return post(path, body, true);
    }

    private MoolreEnvelope post(String path, Object body, boolean usePublicKey) {
        WebClient.RequestBodySpec spec = webClient.post().uri(path);

        if (!sandbox) {
            if (usePublicKey) {
                if (apiPubkey.isBlank()) {
                    throw new IllegalStateException(
                            "MOOLRE_API_PUBKEY is required for live public-key operations.");
                }
                spec = spec.header("X-API-PUBKEY", apiPubkey);
            } else {
                if (apiKey.isBlank()) {
                    throw new IllegalStateException(
                            "MOOLRE_API_KEY is required for live private-key operations.");
                }
                spec = spec.header("X-API-KEY", apiKey);
            }
        }

        String raw = spec
                .bodyValue(body)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, this::toClientException)
                .onStatus(HttpStatusCode::is5xxServerError, this::toServerException)
                // Moolre frequently labels JSON responses as text/html — never
                // decode via bodyToMono(MoolreEnvelope) or Jackson refuses.
                .bodyToMono(String.class)
                .timeout(timeout)
                .block();

        if (raw == null || raw.isBlank()) {
            throw new MoolreServerException("Empty response body from Moolre", 502);
        }
        try {
            return objectMapper.readValue(raw, MoolreEnvelope.class);
        } catch (Exception e) {
            throw new MoolreServerException(
                    "Failed to parse Moolre response: " + summarise(raw), 502);
        }
    }

    private Mono<? extends Throwable> toClientException(ClientResponse response) {
        int status = response.statusCode().value();
        return response.bodyToMono(String.class).defaultIfEmpty("")
                .map(body -> new MoolreClientException(summarise(body), status));
    }

    private Mono<? extends Throwable> toServerException(ClientResponse response) {
        int status = response.statusCode().value();
        return response.bodyToMono(String.class).defaultIfEmpty("")
                .map(body -> new MoolreServerException(summarise(body), status));
    }

    private String summarise(String body) {
        if (body == null || body.isBlank()) {
            return "(empty body)";
        }
        int max = Math.min(body.length(), 200);
        return body.substring(0, max)
                .replaceAll("\"(api[_-]?key|apikey|secret)[^\"]*\"\\s*:\\s*\"[^\"]*\"",
                        "\"$1\":\"REDACTED\"");
    }
}
