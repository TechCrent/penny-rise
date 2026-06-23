package com.stash.kyc.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Local-dev storage implementation. Returns a URL pointing at this
 * service's own upload-confirmation endpoint rather than a real signed
 * cloud-storage URL — sufficient for local development and tests.
 *
 * <p>Active when {@code stash.kyc.storage.provider=local} (the default).
 */
@Component
@ConditionalOnProperty(name = "stash.kyc.storage.provider", havingValue = "local", matchIfMissing = true)
public class LocalFilesystemObjectStorage implements ObjectStorage {

    private static final Logger log = LoggerFactory.getLogger(LocalFilesystemObjectStorage.class);

    private final String baseUrl;
    private final String bucketName;

    public LocalFilesystemObjectStorage(
            @Value("${stash.kyc.base-url}") String baseUrl,
            @Value("${stash.kyc.storage.bucket}") String bucketName) {
        this.baseUrl    = baseUrl;
        this.bucketName = bucketName;
    }

    @Override
    public String generateSignedUploadUrl(String key, String contentType, Duration expiry) {
        long expiresAtEpoch = System.currentTimeMillis() / 1000 + expiry.toSeconds();
        return baseUrl + "/internal/local-storage/upload/" + key
                + "?expires=" + expiresAtEpoch;
    }

    @Override
    public String generateSignedDownloadUrl(String key, Duration expiry) {
        long expiresAtEpoch = System.currentTimeMillis() / 1000 + expiry.toSeconds();
        return baseUrl + "/internal/local-storage/view/" + key + "?expires=" + expiresAtEpoch;
    }

    @Override
    public String getBucketName() {
        return bucketName;
    }

    @Override
    public void delete(String key) {
        // Local dev: no actual file to delete (files were never actually stored
        // in v0.2 — the local signed URL was a stub). Log and succeed.
        log.info("LocalFilesystemObjectStorage: stub delete for key={}", key);
    }
}
