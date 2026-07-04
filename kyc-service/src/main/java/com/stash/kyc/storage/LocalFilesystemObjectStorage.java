package com.stash.kyc.storage;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Local-dev storage implementation. Returns a URL pointing at this
 * service's own upload-confirmation endpoint rather than a real signed
 * cloud-storage URL — sufficient for local development and tests.
 *
 * <p>Active when {@code stash.kyc.storage.provider=local} (the default).
 */
@Component
@ConditionalOnProperty(
    name = "stash.kyc.storage.provider",
    havingValue = "local",
    matchIfMissing = true
)
public class LocalFilesystemObjectStorage implements ObjectStorage {

    private static final Logger log = LoggerFactory.getLogger(
        LocalFilesystemObjectStorage.class
    );

    private final String baseUrl;
    private final String bucketName;

    public LocalFilesystemObjectStorage(
        @Value("${stash.kyc.base-url}") String baseUrl,
        @Value("${stash.kyc.storage.bucket}") String bucketName
    ) {
        this.baseUrl = baseUrl;
        this.bucketName = bucketName;
    }

    @Override
    public String generateSignedUploadUrl(
        String key,
        String contentType,
        Duration expiry
    ) {
        long expiresAtEpoch =
            System.currentTimeMillis() / 1000 + expiry.toSeconds();
        return (
            effectiveBaseUrl() +
            "/internal/local-storage/upload/" +
            key +
            "?expires=" +
            expiresAtEpoch
        );
    }

    @Override
    public String generateSignedDownloadUrl(String key, Duration expiry) {
        long expiresAtEpoch =
            System.currentTimeMillis() / 1000 + expiry.toSeconds();
        return (
            effectiveBaseUrl() +
            "/internal/local-storage/view/" +
            key +
            "?expires=" +
            expiresAtEpoch
        );
    }

    /**
     * Returns the base URL to use for signed URLs, substituting the host (and
     * optionally the scheme) from {@code X-Forwarded-Host} / {@code X-Forwarded-Proto}
     * when a request context is available.
     *
     * <p>The port is always taken from the configured {@code stash.kyc.base-url} so
     * that local-dev port-forwarding rules remain authoritative.
     */
    private String effectiveBaseUrl() {
        try {
            ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null) return baseUrl;

            HttpServletRequest request = attrs.getRequest();
            String forwardedHost = request.getHeader("X-Forwarded-Host");
            if (
                forwardedHost == null || forwardedHost.isBlank()
            ) return baseUrl;

            URI configured = URI.create(baseUrl);
            String scheme = request.getHeader("X-Forwarded-Proto");
            if (scheme == null || scheme.isBlank()) scheme =
                configured.getScheme();

            if (forwardedHost.contains(":")) {
                return scheme + "://" + forwardedHost;
            }

            String forwardedPort = request.getHeader("X-Forwarded-Port");
            int port =
                forwardedPort != null && !forwardedPort.isBlank()
                    ? Integer.parseInt(forwardedPort)
                    : configured.getPort();
            String authority =
                port > 0 ? forwardedHost + ":" + port : forwardedHost;
            return scheme + "://" + authority;
        } catch (Exception e) {
            log.warn(
                "Could not derive base URL from forwarded headers, falling back to configured value",
                e
            );
            return baseUrl;
        }
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
