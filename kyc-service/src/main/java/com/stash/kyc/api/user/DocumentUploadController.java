package com.stash.kyc.api.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stash.kyc.document.api.dto.DocumentUploadConfirmationRequest;
import com.stash.kyc.document.api.dto.DocumentUploadConfirmationResponse;
import com.stash.kyc.document.service.DocumentUploadConfirmationService;
import com.stash.kyc.storage.DocumentUploadSignatureVerifier;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;

/**
 * Confirms a document upload to a KYC submission.
 *
 * <p>Per the HMAC verification pattern (System Design §18.9): the raw
 * request body is captured and signature-verified BEFORE any JSON parsing
 * or business logic runs. An invalid signature is rejected with 401
 * before {@link DocumentUploadConfirmationService} is ever invoked.
 */
@RestController
@RequestMapping("/api/v1/kyc/submissions")
@Tag(name = "KYC Documents", description = "Document upload confirmation")
public class DocumentUploadController {

    private static final Logger log = LoggerFactory.getLogger(DocumentUploadController.class);
    private static final String SIGNATURE_HEADER = "X-Storage-Signature";

    private final DocumentUploadConfirmationService confirmationService;
    private final DocumentUploadSignatureVerifier signatureVerifier;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    public DocumentUploadController(DocumentUploadConfirmationService confirmationService,
                                    DocumentUploadSignatureVerifier signatureVerifier,
                                    ObjectMapper objectMapper,
                                    Validator validator) {
        this.confirmationService = confirmationService;
        this.signatureVerifier   = signatureVerifier;
        this.objectMapper        = objectMapper;
        this.validator           = validator;
    }

    @PostMapping("/{id}/documents")
    @Operation(
        summary     = "Confirm a document upload",
        description = "Called by the storage provider (or test client) after " +
                      "a document has been uploaded via its signed URL. " +
                      "Signature-verified before any processing.")
    @ApiResponse(responseCode = "200", description = "Document confirmed")
    @ApiResponse(responseCode = "401", description = "Invalid or missing signature")
    @ApiResponse(responseCode = "404", description = "Submission not found")
    public ResponseEntity<DocumentUploadConfirmationResponse> confirmUpload(
            @PathVariable UUID id,
            @RequestHeader(SIGNATURE_HEADER) String signature,
            HttpServletRequest request) throws IOException {

        byte[] rawBody = request.getInputStream().readAllBytes();

        if (!signatureVerifier.verify(rawBody, signature)) {
            log.warn("Document upload confirmation rejected: invalid signature");
            return ResponseEntity.status(401).build();
        }

        DocumentUploadConfirmationRequest body =
                objectMapper.readValue(rawBody, DocumentUploadConfirmationRequest.class);

        Set<ConstraintViolation<DocumentUploadConfirmationRequest>> violations = validator.validate(body);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }

        DocumentUploadConfirmationResponse response =
                confirmationService.confirmUpload(id, body);

        return ResponseEntity.ok(response);
    }
}
