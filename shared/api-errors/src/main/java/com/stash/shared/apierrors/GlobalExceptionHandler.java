package com.stash.shared.apierrors;

import com.stash.shared.correlation.CorrelationContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Global exception handler — catches all uncaught exceptions and maps them
 * to the standard error envelope per System Design §6.3.
 *
 * <p>Rules:
 * <ul>
 *   <li>Stack traces never reach the client.</li>
 *   <li>Internal field names never reach the client.</li>
 *   <li>Full stack traces are always logged server-side with the correlation_id.</li>
 *   <li>The correlation_id is read from the {@code X-Correlation-Id} request header
 *       if present; a random UUID v4 is generated if absent.</li>
 * </ul>
 *
 * <p>This handler is a shared library. Each service activates it by adding
 * {@code shared/api-errors} as a dependency and including the package in its
 * component scan via {@code @SpringBootApplication} or explicit
 * {@code @ComponentScan(basePackages = "com.stash.shared.apierrors")}.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // ── Domain exceptions ──────────────────────────────────────────────────

    @ExceptionHandler(StashApiException.class)
    public ResponseEntity<ErrorResponse> handleDomainException(
            StashApiException ex, HttpServletRequest request) {

        String correlationId = correlationId(request);
        log.warn("[{}] Domain exception: code={} status={} message={}",
                correlationId, ex.getErrorCode(), ex.getHttpStatus(), ex.getMessage());

        return ResponseEntity
                .status(ex.getHttpStatus())
                .body(ErrorResponse.of(
                        ex.getErrorCode(),
                        ex.getMessage(),
                        ex.getDetails(),
                        correlationId));
    }

    // ── Validation: @Valid on request body ─────────────────────────────────

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {

        String correlationId = correlationId(request);

        Map<String, Object> fieldErrors = new LinkedHashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(fe.getField(), fe.getDefaultMessage());
        }

        log.debug("[{}] Validation failure: {}", correlationId, fieldErrors);

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(
                        ErrorCode.VALIDATION_ERROR,
                        "Request validation failed. See details for field errors.",
                        fieldErrors,
                        correlationId));
    }

    // ── Validation: constraint violations (path/query params) ──────────────

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(
            ConstraintViolationException ex, HttpServletRequest request) {

        String correlationId = correlationId(request);
        Map<String, Object> violations = new LinkedHashMap<>();
        ex.getConstraintViolations().forEach(cv ->
                violations.put(cv.getPropertyPath().toString(), cv.getMessage()));

        log.debug("[{}] Constraint violations: {}", correlationId, violations);

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(
                        ErrorCode.VALIDATION_ERROR,
                        "Request validation failed.",
                        violations,
                        correlationId));
    }

    // ── Malformed request body ─────────────────────────────────────────────

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadable(
            HttpMessageNotReadableException ex, HttpServletRequest request) {

        String correlationId = correlationId(request);
        log.debug("[{}] Unreadable request body", correlationId);

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(
                        ErrorCode.VALIDATION_ERROR,
                        "Request body is missing or malformed.",
                        Map.of(),
                        correlationId));
    }

    // ── Type mismatch (e.g. non-UUID path variable) ────────────────────────

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {

        String correlationId = correlationId(request);
        log.debug("[{}] Type mismatch: param={}", correlationId, ex.getName());

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(
                        ErrorCode.VALIDATION_ERROR,
                        "Invalid value for parameter: " + ex.getName(),
                        Map.of("parameter", ex.getName()),
                        correlationId));
    }

    // ── Missing required headers ───────────────────────────────────────────

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErrorResponse> handleMissingHeader(
            MissingRequestHeaderException ex, HttpServletRequest request) {

        String correlationId = correlationId(request);
        log.debug("[{}] Missing required header: {}", correlationId, ex.getHeaderName());

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(
                        ErrorCode.VALIDATION_ERROR,
                        "Required header is missing: " + ex.getHeaderName(),
                        Map.of("header", ex.getHeaderName()),
                        correlationId));
    }

    // ── 404 from Spring MVC (no handler found) ─────────────────────────────

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResource(
            NoResourceFoundException ex, HttpServletRequest request) {

        String correlationId = correlationId(request);
        log.debug("[{}] No resource found: {}", correlationId, request.getRequestURI());

        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(
                        ErrorCode.NOT_FOUND,
                        "The requested resource does not exist.",
                        Map.of(),
                        correlationId));
    }

    // ── Catch-all: unexpected exceptions ──────────────────────────────────

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(
            Exception ex, HttpServletRequest request) {

        String correlationId = correlationId(request);

        // Full stack trace logged server-side — NEVER sent to client
        log.error("[{}] Unexpected exception: {}", correlationId, ex.getMessage(), ex);

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of(
                        ErrorCode.INTERNAL_ERROR,
                        "An unexpected error occurred. Quote the correlation ID when contacting support.",
                        Map.of(),
                        correlationId));
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private String correlationId(HttpServletRequest request) {
        String fromContext = CorrelationContext.get();
        if (fromContext != null && !fromContext.isBlank()) {
            return fromContext;
        }
        String header = request.getHeader(CorrelationContext.HEADER);
        return (header != null && !header.isBlank()) ? header : UUID.randomUUID().toString();
    }
}