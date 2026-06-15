package com.stash.shared.apierrors;

import com.stash.shared.correlation.CorrelationIdFilter;
import com.stash.shared.correlation.CorrelationIdRabbitConfig;
import com.stash.shared.correlation.CorrelationIdWebClientConfig;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

@AutoConfiguration
@Import({
        GlobalExceptionHandler.class,
        CorrelationIdWebClientConfig.class,
        CorrelationIdRabbitConfig.class
})
public class ApiErrorsAutoConfiguration {

    @Bean
    public CorrelationIdFilter correlationIdFilter() {
        return new CorrelationIdFilter();
    }
}
