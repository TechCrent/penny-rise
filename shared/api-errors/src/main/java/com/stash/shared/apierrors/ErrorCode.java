package com.stash.shared.apierrors;

/**
 * Canonical enumeration of all client-facing error codes for the Stash platform.
 *
 * <p>Conventions per System Design §6.3:
 * <ul>
 *   <li>UPPER_SNAKE_CASE</li>
 *   <li>Domain-prefixed: AUTH_*, KYC_*, VAULT_*, SUSU_*, TRANSFER_*</li>
 *   <li>Cross-cutting codes are unprefixed: INTERNAL_ERROR, VALIDATION_ERROR, etc.</li>
 *   <li>Codes are stable — once shipped, a code's meaning never changes.</li>
 *   <li>New codes are added here; deprecated codes are marked but never removed.</li>
 * </ul>
 *
 * <p>This file is the single source of truth. The API reference is generated from it.
 * Mobile and admin clients may switch on these codes exhaustively.
 */
public enum ErrorCode {

    // ── Cross-cutting ──────────────────────────────────────────────────────

    /** Unexpected server-side failure. Details in server logs under correlation_id. */
    INTERNAL_ERROR,

    /** Request body or query parameters failed validation. See details for field errors. */
    VALIDATION_ERROR,

    /** The requested resource does not exist or is not visible to this caller. */
    NOT_FOUND,

    /** Request conflicts with current state. See message for specifics. */
    CONFLICT,

    /** The caller is not authenticated. */
    UNAUTHORIZED,

    /** The caller is authenticated but not permitted to perform this action. */
    FORBIDDEN,

    // ── AUTH domain ────────────────────────────────────────────────────────

    /** Email or password is incorrect. */
    AUTH_INVALID_CREDENTIALS,

    /** Account is locked due to repeated failed login attempts. */
    AUTH_ACCOUNT_LOCKED,

    /** The provided token (access or refresh) is missing, malformed, or expired. */
    AUTH_TOKEN_INVALID,

    /** Email address has not been verified. */
    AUTH_EMAIL_NOT_VERIFIED,

    /** Email address is already registered to an existing account. */
    AUTH_EMAIL_ALREADY_REGISTERED,

    /** Email verification token has expired. */
    AUTH_VERIFICATION_TOKEN_EXPIRED,

    /** Email verification token has already been used. */
    AUTH_VERIFICATION_TOKEN_ALREADY_USED,

    /** Email verification token not found or invalid. */
    AUTH_VERIFICATION_TOKEN_INVALID,

    /** Resend rate limit exceeded. */
    AUTH_RESEND_RATE_LIMIT_EXCEEDED,

    /** Email is already verified. */
    AUTH_EMAIL_ALREADY_VERIFIED,

    // ── USER domain ───────────────────────────────────────────────────────

    /** A pending account deletion request already exists for this user. */
    USER_DELETION_REQUEST_ALREADY_PENDING,

    // ── KYC domain ────────────────────────────────────────────────────────

    /** KYC submission not found. */
    KYC_SUBMISSION_NOT_FOUND,

    /** A KYC submission already exists for this user and cannot be duplicated. */
    KYC_SUBMISSION_ALREADY_EXISTS,

    /** KYC is required to perform this action. */
    KYC_REQUIRED,

    /** KYC submission was rejected. */
    KYC_SUBMISSION_REJECTED,

    // ── VAULT domain ──────────────────────────────────────────────────────

    /** Vault not found or does not belong to this user. */
    VAULT_NOT_FOUND,

    /** Vault balance is insufficient for the requested operation. */
    VAULT_INSUFFICIENT_BALANCE,

    /** Vault is locked and cannot be withdrawn from before the maturity date. */
    VAULT_LOCKED,

    /** Vault has already been closed. */
    VAULT_ALREADY_CLOSED,

    // ── SUSU domain ───────────────────────────────────────────────────────

    /** Susu group not found. */
    SUSU_GROUP_NOT_FOUND,

    /** User is already a member of this susu group. */
    SUSU_ALREADY_MEMBER,

    /** Susu group is full and cannot accept new members. */
    SUSU_GROUP_FULL,

    /** Susu round is not in a state that permits this action. */
    SUSU_ROUND_INVALID_STATE,

    // ── TRANSFER domain ───────────────────────────────────────────────────

    /** Transfer not found. */
    TRANSFER_NOT_FOUND,

    /** Recipient not found or not eligible to receive transfers. */
    TRANSFER_RECIPIENT_NOT_FOUND,

    /** Daily or monthly transfer limit has been exceeded. */
    TRANSFER_LIMIT_EXCEEDED,

    /** Idempotency key was reused for a different request body. */
    IDEMPOTENCY_KEY_MISUSED,
}