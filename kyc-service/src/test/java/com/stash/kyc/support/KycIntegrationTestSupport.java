package com.stash.kyc.support;

import java.util.Base64;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Shared Testcontainers + crypto config for kyc-service {@code @SpringBootTest}
 * classes. Centralises the 32-byte AES key so new tests cannot accidentally
 * copy the old 31-byte string that fails {@code GhanaCardCryptoConfig}.
 */
public final class KycIntegrationTestSupport {

    /** Exactly 32 bytes — required for AES-256. */
    public static final byte[] AES_256_KEY_BYTES =
        "test-aes-256-key-32-bytes-long!!".getBytes();

    public static final String AES_256_KEY_BASE64 =
        Base64.getEncoder().encodeToString(AES_256_KEY_BYTES);

    static {
        if (AES_256_KEY_BYTES.length != 32) {
            throw new ExceptionInInitializerError(
                "KycIntegrationTestSupport AES_256_KEY_BYTES must be exactly 32 bytes"
            );
        }
    }

    private KycIntegrationTestSupport() {}

    public static void registerPostgres(
        DynamicPropertyRegistry registry,
        PostgreSQLContainer<?> postgres
    ) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add(
            "stash.kyc.ghana-card-encryption-key",
            () -> AES_256_KEY_BASE64
        );
        // DynamicPropertySource sits above OS env vars in Spring Boot's precedence chain,
        // so this reliably overrides any RABBITMQ_* env vars that would otherwise
        // restore auto-startup to true and cause listener containers to connect.
        registry.add(
            "spring.rabbitmq.listener.simple.auto-startup",
            () -> "false"
        );
    }
}
