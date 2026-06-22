package com.stash.kyc.storage;

/**
 * Verifies that an inbound document-upload confirmation request actually
 * came from the configured storage provider.
 *
 * <p>Per the HMAC verification pattern established for Payments webhooks
 * (System Design §18.9 / Must Research doc "HMAC Webhook Signature
 * Verification"): the signature is computed over the RAW request body
 * bytes, compared in constant time, and verification happens BEFORE any
 * parsing or business logic.
 *
 * <p>Implementations are selected by storage provider — a real Supabase
 * implementation reads Supabase's actual webhook secret format; the local
 * dev implementation (see {@link LocalSignatureVerifier}) is a no-op stand-in
 * since local filesystem "uploads" have no real webhook delivery to verify.
 */
public interface DocumentUploadSignatureVerifier {

    /**
     * Verifies the signature on a raw webhook payload.
     *
     * @param rawBody          the exact bytes of the request body, captured
     *                         before any JSON parsing
     * @param signatureHeader  the signature value from the request header
     * @return true if the signature is valid, false otherwise
     */
    boolean verify(byte[] rawBody, String signatureHeader);
}
