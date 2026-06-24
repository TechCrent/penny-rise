package com.stash.payments.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class PaymentsConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
