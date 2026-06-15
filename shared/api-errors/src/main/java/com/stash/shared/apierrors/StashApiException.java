package com.stash.shared.apierrors;

import org.springframework.http.HttpStatus;

import java.util.Map;

/**
 * Base exception for all domain-level errors in the Stash platform.
 *
 * <p>Subclass this for each domain error. The global exception handler
 * {@link GlobalExceptionHandler} catches every {@code StashApiException}
 * and maps it to the standard error envelope.
 *
 * <h2>Usage</h2>
 * <pre>
 *   // Define once per error type:
 *   public class VaultNotFoundException extends StashApiException {
 *       public VaultNotFoundException(UUID vaultId) {
 *           super(ErrorCode.VAULT_NOT_FOUND,
 *                 "Vault not found: " + vaultId,
 *                 HttpStatus.NOT_FOUND,
 *                 Map.of("vault_id", vaultId.toString()));
 *       }
 *   }
 *
 *   // Throw from service layer:
 *   throw new VaultNotFoundException(vaultId);
 * </pre>
 */
public class StashApiException extends RuntimeException {

    private final ErrorCode errorCode;
    private final HttpStatus httpStatus;
    private final Map<String, Object> details;

    public StashApiException(ErrorCode errorCode,
                             String message,
                             HttpStatus httpStatus) {
        this(errorCode, message, httpStatus, Map.of());
    }

    public StashApiException(ErrorCode errorCode,
                             String message,
                             HttpStatus httpStatus,
                             Map<String, Object> details) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
        this.details = details != null ? Map.copyOf(details) : Map.of();
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    public Map<String, Object> getDetails() {
        return details;
    }
}