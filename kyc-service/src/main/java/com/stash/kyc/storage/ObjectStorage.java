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
     * Generates a signed, time-limited URL for VIEWING (downloading) an
     * already-uploaded document. Used by admin review endpoints to render
     * document images without exposing the bucket publicly.
     *
     * @param key    the storage path/key of the document
     * @param expiry how long the signed URL remains valid
     * @return a signed URL the admin console can fetch the image from
     */
    String generateSignedDownloadUrl(String key, Duration expiry);

    /**
     * Returns the bucket/container name documents are stored in.
     */
    String getBucketName();

    /**
     * Permanently deletes a document from storage.
     *
     * @param key the storage path/key of the document to delete
     * @throws com.stash.kyc.document.exception.DocumentDeletionStorageException if deletion fails
     */
    void delete(String key);
}
