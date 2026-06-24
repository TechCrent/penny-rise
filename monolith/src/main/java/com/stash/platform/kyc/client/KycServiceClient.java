package com.stash.platform.kyc.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.UUID;

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

    public KycServiceClient(@Value("${stash.kyc.service-url}") String serviceUrl,
                            WebClient correlationAwareWebClient) {
        this.webClient = correlationAwareWebClient.mutate()
                .baseUrl(serviceUrl)
                .build();
    }

    public ResponseEntity<byte[]> forward(HttpMethod method,
                                          String path,
                                          UUID userId,
                                          byte[] body,
                                          HttpHeaders extraHeaders) {
        WebClient.RequestBodySpec request = webClient.method(method)
                .uri(path)
                .header(USER_ID_HEADER, userId.toString())
                .contentType(MediaType.APPLICATION_JSON);

        if (extraHeaders != null) {
            extraHeaders.forEach((name, values) ->
                    values.forEach(value -> request.header(name, value)));
        }

        WebClient.RequestHeadersSpec<?> spec = body != null && body.length > 0
                ? request.body(BodyInserters.fromValue(body))
                : request;

        return spec.exchangeToMono(response ->
                response.bodyToMono(byte[].class)
                        .defaultIfEmpty(new byte[0])
                        .map(responseBody -> {
                            HttpHeaders headers = new HttpHeaders();
                            response.headers().contentType().ifPresent(contentType ->
                                    headers.setContentType(MediaType.parseMediaType(contentType.toString())));
                            return ResponseEntity.status(response.statusCode())
                                    .headers(headers)
                                    .body(responseBody);
                        }))
                .block();
    }
}
