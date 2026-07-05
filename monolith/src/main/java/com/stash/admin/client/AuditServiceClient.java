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
 * Internal HTTP client for the audit-service admin API.
 *
 * <p>The admin console routes all calls through the monolith. This client
 * forwards the admin Bearer token so audit-service's
 * {@code AuditAdminJwtAuthenticationFilter} can verify it — both services
 * share {@code JWT_SIGNING_KEY}, so the token the monolith issued is
 * directly verifiable by audit-service without any extra translation.
 */
@Component
public class AuditServiceClient {

    private final WebClient webClient;

    public AuditServiceClient(
            WebClient.Builder builder,
            @Value("${stash.audit.service-url}") String serviceUrl) {
        this.webClient = builder.baseUrl(serviceUrl).build();
    }

    public ResponseEntity<byte[]> forward(HttpMethod method, String pathAndQuery,
                                          String bearerToken, byte[] body) {
        WebClient.RequestBodySpec request = webClient.method(method).uri(pathAndQuery);
        request.header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken);

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
