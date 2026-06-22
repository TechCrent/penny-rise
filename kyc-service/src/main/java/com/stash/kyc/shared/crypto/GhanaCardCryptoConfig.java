package com.stash.kyc.shared.crypto;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * Initialises {@link GhanaCardEncryptionConverter} before JPA starts.
 *
 * <p>Hibernate instantiates JPA {@code AttributeConverter} classes itself
 * (not via Spring), so the encryption key is loaded here and held in a
 * static field the converter reads from.
 */
@Configuration
public class GhanaCardCryptoConfig {

    private static final Logger log = LoggerFactory.getLogger(GhanaCardCryptoConfig.class);

    public GhanaCardCryptoConfig(
            @Value("${stash.kyc.ghana-card-encryption-key}") String base64Key) {
        GhanaCardEncryptionConverter.initialize(base64Key);
        log.info("GhanaCardEncryptionConverter initialised with AES-256-GCM");
    }
}
