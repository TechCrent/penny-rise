package com.stash.kyc.storage;

import java.time.Duration;

/**
 * Abstraction over object storage for KYC documents.
 *
 * <p>Implementations: {@link LocalFilesystemObjectStorage} (dev) and
 * a Supabase-backed implementation (wired in v0.2-022). Selected by
 * the {@code stash.kyc.storage.provider} property.
 */
public interface ObjectStorage {

    /**
     * Generates a signed upload URL for a client to PUT a document directly.
     *
     * @param key            the storage path/key the document will be stored at
     * @param contentType    expected MIME type
     * @param expiry         how long the signed URL remains valid
     * @return a signed URL the client can PUT to
     */
    String generateSignedUploadUrl(String key, String contentType, Duration expiry);

    /**
     * Returns the bucket/container name documents are stored in.
     */
    String getBucketName();
}
