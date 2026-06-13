package com.stash.shared.correlation;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * Configures a {@link WebClient} bean that automatically attaches
 * {@code X-Correlation-Id} to every outbound HTTP request.
 *
 * <p>Services that make HTTP calls to other internal services should inject
 * this bean rather than constructing their own {@link WebClient}:
 *
 * <pre>
 *   {@literal @}Service
 *   public class IntegrationPaymentsClient {
 *       private final WebClient webClient;
 *
 *       public IntegrationPaymentsClient(WebClient webClient) {
 *           this.webClient = webClient;
 *       }
 *   }
 * </pre>
 *
 * <p>The filter reads the correlation ID from {@link CorrelationContext} at
 * the time the request is executed — not at construction time — so it always
 * picks up the correct ID for the current thread.
 */
@Configuration
public class CorrelationIdWebClientConfig {

    @Bean
    public WebClient correlationAwareWebClient(WebClient.Builder builder) {
        return builder
                .filter(correlationIdExchangeFilter())
                .build();
    }

    private ExchangeFilterFunction correlationIdExchangeFilter() {
        return ExchangeFilterFunction.ofRequestProcessor(clientRequest -> {
            String correlationId = CorrelationContext.get();
            if (correlationId == null || correlationId.isBlank()) {
                return Mono.just(clientRequest);
            }
            ClientRequest withHeader = ClientRequest.from(clientRequest)
                    .header(CorrelationContext.HEADER, correlationId)
                    .build();
            return Mono.just(withHeader);
        });
    }
}
