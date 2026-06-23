package com.stash.kyc.document.exception;

/**
 * Thrown when a storage provider call to delete a document fails.
 * Caught by {@link com.stash.kyc.document.service.DocumentDeletionWorker}
 * to record the failure and continue with remaining documents.
 */
public class DocumentDeletionStorageException extends RuntimeException {

    private final String storageKey;

    public DocumentDeletionStorageException(String storageKey, String message, Throwable cause) {
        super(message, cause);
        this.storageKey = storageKey;
    }

    public String getStorageKey() {
        return storageKey;
    }
}
