package com.stash.platform.kyc.client;

import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Forwards authenticated customer KYC requests to kyc-service.
 *
 * <p>The monolith validates the customer JWT and injects
 * {@code X-Authenticated-User-Id}; kyc-service lives in the internal
 * trust zone and does not validate customer JWTs directly.
 */
@Component
public class KycServiceClient {

    static final String USER_ID_HEADER = "X-Authenticated-User-Id";

    private final WebClient webClient;

    public KycServiceClient(
        @Value("${stash.kyc.service-url}") String serviceUrl,
        WebClient correlationAwareWebClient
    ) {
        this.webClient = correlationAwareWebClient
            .mutate()
            .baseUrl(serviceUrl)
            .build();
    }

    public ResponseEntity<byte[]> forward(
        HttpMethod method,
        String path,
        UUID userId,
        byte[] body,
        HttpHeaders extraHeaders
    ) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(USER_ID_HEADER, userId.toString());
        if (extraHeaders != null) {
            headers.addAll(extraHeaders);
        }
        return exchange(
            method,
            path,
            body,
            MediaType.APPLICATION_JSON,
            headers
        );
    }

    public ResponseEntity<byte[]> forwardPublic(
        HttpMethod method,
        String path,
        byte[] body,
        MediaType contentType,
        HttpHeaders extraHeaders
    ) {
        return exchange(method, path, body, contentType, extraHeaders);
    }

    private ResponseEntity<byte[]> exchange(
        HttpMethod method,
        String path,
        byte[] body,
        MediaType contentType,
        HttpHeaders headers
    ) {
        WebClient.RequestBodySpec request = webClient.method(method).uri(path);

        if (contentType != null) {
            request.contentType(contentType);
        }

        if (headers != null) {
            headers.forEach((name, values) ->
                values.forEach(value -> request.header(name, value))
            );
        }

        WebClient.RequestHeadersSpec<?> spec =
            body != null && body.length > 0
                ? request.body(BodyInserters.fromValue(body))
                : request;

        return spec
            .exchangeToMono(response ->
                response
                    .bodyToMono(byte[].class)
                    .defaultIfEmpty(new byte[0])
                    .map(responseBody -> {
                        HttpHeaders responseHeaders = new HttpHeaders();
                        response
                            .headers()
                            .contentType()
                            .ifPresent(responseHeaders::setContentType);
                        return ResponseEntity.status(response.statusCode())
                            .headers(responseHeaders)
                            .body(responseBody);
                    })
            )
            .block();
    }
}
