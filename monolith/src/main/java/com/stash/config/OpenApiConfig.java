package com.stash.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI / Swagger UI configuration for the monolith.
 *
 * <p>Available at:
 * <ul>
 *   <li>{@code /v3/api-docs} — raw OpenAPI 3 JSON spec</li>
 *   <li>{@code /swagger-ui.html} — interactive Swagger UI</li>
 * </ul>
 *
 * <p>Both are disabled in the {@code prod} profile via
 * {@code application-prod.yml} ({@code springdoc.swagger-ui.enabled=false},
 * {@code springdoc.api-docs.enabled=false}).
 *
 * <p>Convention (effective v0.2 onwards): every new endpoint must be
 * annotated with {@code @Operation}, {@code @ApiResponse}, and
 * {@code @Parameter} where applicable, so the generated spec stays
 * informative. See the project README "API Documentation Convention" section.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI monolithOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Stash Monolith API")
                        .version("v0.1.0")
                        .description("Core platform API: auth, vaults, susu groups, " +
                                "transfers, challenges, expenses, notifications, admin."));
    }
}