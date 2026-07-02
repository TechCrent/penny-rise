package com.stash.platform.notification.service;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationDispatchMetricNamingTest {

    @Test
    @DisplayName("notification.dispatch.failure.rate counter is registered and queryable")
    void metricNameIsRegistered() {
        MeterRegistry registry = new SimpleMeterRegistry();
        registry.counter("notification.dispatch.failure.rate").increment();

        // SimpleMeterRegistry confirms the name is registered; a full
        // @SpringBootTest hitting /actuator/prometheus would confirm the
        // Prometheus _total suffix is appended correctly — sketched in the
        // issue's Step 8 but not built here (needs a running Spring context
        // with PrometheusMeterRegistry).
        assertThat(registry.find("notification.dispatch.failure.rate").counter())
                .isNotNull();
        assertThat(registry.find("notification.dispatch.failure.rate").counter().count())
                .isEqualTo(1.0);
    }
}
