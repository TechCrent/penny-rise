package com.stash.kyc.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI / Swagger UI configuration for the KYC Service.
 * See {@code com.stash.config.OpenApiConfig} in the monolith for full
 * documentation of conventions — identical here.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI kycOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Stash KYC Service API")
                        .version("v0.1.0")
                        .description("Internal API: identity verification submissions, " +
                                "document upload, manual review queue."));
    }
}