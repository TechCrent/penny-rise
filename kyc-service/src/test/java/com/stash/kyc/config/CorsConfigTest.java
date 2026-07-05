package com.stash.kyc.config;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import static org.assertj.core.api.Assertions.assertThat;

class CorsConfigTest {

    @Test
    void createsMvcConfigurerBean() {
        CorsConfig config = new CorsConfig();

        WebMvcConfigurer corsConfigurer = config.corsConfigurer();

        assertThat(corsConfigurer).isNotNull();
    }
}
