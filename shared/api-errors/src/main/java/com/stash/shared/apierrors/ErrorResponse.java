package com.stash.shared.apierrors;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

/**
 * Standard error response envelope per System Design §6.3.
 *
 * <p>Serialises to:
 * <pre>
 * {
 *   "error": {
 *     "code": "VAULT_INSUFFICIENT_BALANCE",
 *     "message": "Vault balance is GHS 50; requested GHS 100.",
 *     "details": { "available": 5000, "requested": 10000 },
 *     "correlation_id": "9f8e7d6c-..."
 *   }
 * }
 * </pre>
 *
 * <p>The {@code details} field is omitted from the response when empty
 * (via {@link JsonInclude}).
 */
public record ErrorResponse(ErrorBody error) {

    public static ErrorResponse of(String code,
                                   String message,
                                   Map<String, Object> details,
                                   String correlationId) {
        return new ErrorResponse(new ErrorBody(code, message, details, correlationId));
    }

    public static ErrorResponse of(ErrorCode code,
                                   String message,
                                   Map<String, Object> details,
                                   String correlationId) {
        return of(code.name(), message, details, correlationId);
    }

    /**
     * Inner body — maps to the "error" key in the JSON response.
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record ErrorBody(
            String code,
            String message,
            Map<String, Object> details,
            String correlation_id
    ) {}
}