package com.stash.payments.idempotency.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stash.payments.idempotency.service.IdempotencyService;
import com.stash.payments.idempotency.service.IdempotencyService.ClaimResult;
import com.stash.payments.idempotency.service.RequestHasher;
import com.stash.payments.shared.web.CachedBodyHttpServletRequestWrapper;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

/**
 * Servlet filter that enforces idempotency on all mutating HTTP requests.
 *
 * <p>Mutating methods: POST, PUT, PATCH, DELETE (per System Design §6.1).
 *
 * <p>The filter runs early in the chain (Order 10) — before Spring Security
 * but after the correlation-ID filter. It wraps the request in a
 * {@link CachedBodyHttpServletRequestWrapper} so the body can be read
 * both here (for hashing) and downstream (by the handler).
 */
@Component
@Order(10)
public class IdempotencyFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyFilter.class);
    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    private static final Set<String> MUTATING_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

    private final IdempotencyService idempotencyService;
    private final RequestHasher hasher;
    private final ObjectMapper objectMapper;

    public IdempotencyFilter(IdempotencyService idempotencyService,
                             RequestHasher hasher,
                             ObjectMapper objectMapper) {
        this.idempotencyService = idempotencyService;
        this.hasher             = hasher;
        this.objectMapper       = objectMapper;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response,
                         FilterChain chain) throws IOException, ServletException {

        HttpServletRequest  req  = (HttpServletRequest)  request;
        HttpServletResponse resp = (HttpServletResponse) response;

        if (!MUTATING_METHODS.contains(req.getMethod())) {
            chain.doFilter(request, response);
            return;
        }

        String keyValue = req.getHeader(IDEMPOTENCY_KEY_HEADER);
        if (!StringUtils.hasText(keyValue)) {
            writeError(resp, 400, "IDEMPOTENCY_KEY_MISSING",
                    "Mutating requests must include an Idempotency-Key header.");
            return;
        }

        // Wrap so the body can be read more than once
        CachedBodyHttpServletRequestWrapper wrappedReq =
                new CachedBodyHttpServletRequestWrapper(req);
        byte[] body = wrappedReq.getCachedBody();
        String hash = hasher.hash(req.getMethod(), req.getRequestURI(), body);

        ClaimResult result = idempotencyService.claim(keyValue, hash, req.getRequestURI());

        switch (result) {
            case ClaimResult.Proceed ignored -> {
                // First request — let it through, then cache the response
                CachingResponseWrapper cachedResp = new CachingResponseWrapper(resp);
                try {
                    chain.doFilter(wrappedReq, cachedResp);
                    String cachedBody = cachedResp.getCachedBody();
                    idempotencyService.markCompleted(keyValue, cachedResp.getStatus(), cachedBody);
                    cachedResp.copyBodyToResponse();
                } catch (Exception e) {
                    // Handler threw — mark failed so retries get the cached error
                    String errorJson = errorJson("INTERNAL_ERROR", "Request processing failed.");
                    idempotencyService.markFailed(keyValue, 500, errorJson);
                    writeError(resp, 500, "INTERNAL_ERROR", "Request processing failed.");
                }
            }
            case ClaimResult.Cached c -> {
                // Retry with same hash — return cached response verbatim
                log.debug("Idempotency cache hit for key '{}'", keyValue);
                resp.setStatus(c.statusCode());
                resp.setContentType(MediaType.APPLICATION_JSON_VALUE);
                resp.getWriter().write(c.body());
            }
            case ClaimResult.KeyReused ignored ->
                writeError(resp, 422, "IDEMPOTENCY_KEY_REUSED",
                        "The Idempotency-Key was already used for a different request. " +
                        "Generate a fresh key for each distinct operation.");
            case ClaimResult.InProgress ignored ->
                writeError(resp, 409, "IDEMPOTENCY_KEY_IN_PROGRESS",
                        "A request with this Idempotency-Key is already being processed. " +
                        "Retry with backoff.");
        }
    }

    private void writeError(HttpServletResponse resp, int status,
                             String code, String message) throws IOException {
        resp.setStatus(status);
        resp.setContentType(MediaType.APPLICATION_JSON_VALUE);
        resp.getWriter().write(errorJson(code, message));
    }

    private String errorJson(String code, String message) {
        try {
            return objectMapper.writeValueAsString(Map.of("code", code, "message", message));
        } catch (Exception e) {
            return "{\"code\":\"" + code + "\",\"message\":\"" + message + "\"}";
        }
    }
}
