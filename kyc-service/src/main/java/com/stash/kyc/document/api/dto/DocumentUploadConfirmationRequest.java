package com.stash.kyc.document.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Request body for POST /api/v1/kyc/submissions/{id}/documents.
 *
 * <p>Per System Design doc's public API table: "Confirm a document has
 * been uploaded; KYC verifies the SHA-256 hash."
 */
public record DocumentUploadConfirmationRequest(

        @NotBlank(message = "provider_event_id is required")
        @JsonProperty("provider_event_id")
        String providerEventId,

        @NotBlank(message = "document_type is required")
        @JsonProperty("document_type")
        String documentType,

        @NotBlank(message = "storage_key is required")
        @JsonProperty("storage_key")
        String storageKey,

        @NotBlank(message = "content_type is required")
        @JsonProperty("content_type")
        String contentType,

        @NotNull(message = "size_bytes is required")
        @Positive(message = "size_bytes must be positive")
        @JsonProperty("size_bytes")
        Long sizeBytes,

        @NotBlank(message = "sha256_hash is required")
        @JsonProperty("sha256_hash")
        String sha256Hash
) {}
