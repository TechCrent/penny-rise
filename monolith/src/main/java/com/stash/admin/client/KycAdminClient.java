package com.stash.admin.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Internal HTTP client for the kyc-service admin API.
 *
 * <p>The admin console routes all calls through the monolith. The monolith
 * validates the admin JWT, then forwards to kyc-service with the shared
 * placeholder admin token ({@code X-Admin-Token}) so kyc-service's
 * {@code PlaceholderAdminAuthFilter} accepts the request.
 *
 * <p>Replace entirely when v0.5 ships real admin JWT verification in
 * kyc-service — at that point forward the Bearer token directly, same as
 * {@link AuditServiceClient}.
 */
@Component
public class KycAdminClient {

    private final WebClient webClient;
    private final String placeholderAdminToken;

    public KycAdminClient(
            WebClient.Builder builder,
            @Value("${stash.kyc.service-url}") String serviceUrl,
            @Value("${stash.kyc.placeholder-admin-token}") String placeholderAdminToken) {
        this.webClient = builder.baseUrl(serviceUrl).build();
        this.placeholderAdminToken = placeholderAdminToken;
    }

    public ResponseEntity<byte[]> forward(HttpMethod method, String pathAndQuery, byte[] body) {
        return forward(method, pathAndQuery, body, null);
    }

    public ResponseEntity<byte[]> forward(HttpMethod method, String pathAndQuery, byte[] body,
                                          HttpHeaders extraHeaders) {
        WebClient.RequestBodySpec request = webClient.method(method).uri(pathAndQuery);
        request.header("X-Admin-Token", placeholderAdminToken);
        if (extraHeaders != null) {
            extraHeaders.forEach((name, values) ->
                    values.forEach(value -> request.header(name, value)));
        }

        WebClient.RequestHeadersSpec<?> spec =
                body != null && body.length > 0
                        ? request.contentType(MediaType.APPLICATION_JSON)
                                  .body(BodyInserters.fromValue(body))
                        : request;

        return spec.exchangeToMono(response ->
                response.bodyToMono(byte[].class)
                        .defaultIfEmpty(new byte[0])
                        .map(responseBody -> {
                            HttpHeaders responseHeaders = new HttpHeaders();
                            response.headers().contentType()
                                    .ifPresent(responseHeaders::setContentType);
                            return ResponseEntity.status(response.statusCode())
                                    .headers(responseHeaders)
                                    .body(responseBody);
                        })
        ).block();
    }
}
