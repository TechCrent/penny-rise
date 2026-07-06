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
 * validates the admin JWT, then forwards it as a Bearer token — kyc-service
 * shares {@code JWT_SIGNING_KEY} and verifies it directly via its own
 * {@code AdminJwtAuthenticationFilter}, same as {@link AuditServiceClient}.
 * This is what makes {@code reviewer_admin_id} the real logged-in admin's
 * id instead of a placeholder sentinel.
 */
@Component
public class KycAdminClient {

    private final WebClient webClient;

    public KycAdminClient(
            WebClient.Builder builder,
            @Value("${stash.kyc.service-url}") String serviceUrl) {
        this.webClient = builder.baseUrl(serviceUrl).build();
    }

    public ResponseEntity<byte[]> forward(HttpMethod method, String pathAndQuery, String bearerToken, byte[] body) {
        return forward(method, pathAndQuery, bearerToken, body, null);
    }

    public ResponseEntity<byte[]> forward(HttpMethod method, String pathAndQuery, String bearerToken, byte[] body,
                                          HttpHeaders extraHeaders) {
        WebClient.RequestBodySpec request = webClient.method(method).uri(pathAndQuery);
        request.header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken);
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
