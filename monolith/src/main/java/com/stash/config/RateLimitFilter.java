package com.stash.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stash.shared.apierrors.ErrorCode;
import com.stash.shared.apierrors.ErrorResponse;
import com.stash.shared.correlation.CorrelationContext;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory, per-caller rate limiting — gap-analysis fix: the only
 * throttling anywhere in the platform was the login-attempt lockout
 * ({@code LoginAttemptTracker}); every other endpoint, including money
 * movement (deposits, transfers, susu contributions), had no rate limit
 * at all.
 *
 * <p>Two tiers, both keyed by the authenticated principal's id when
 * present (falls back to remote IP for unauthenticated requests):
 * <ul>
 *   <li>General: {@link #GENERAL_CAPACITY} requests/minute on every
 *       request.</li>
 *   <li>Financial: an additional, tighter {@link #FINANCIAL_CAPACITY}
 *       requests/minute specifically on money-movement mutation
 *       endpoints (deposits, withdrawals, transfers, susu
 *       contributions).</li>
 * </ul>
 *
 * <p><strong>Single-instance only.</strong> Buckets live in a
 * {@link ConcurrentHashMap} in this process's memory — correct for the
 * monolith's current single-instance deployment, but would need a
 * shared store (e.g. Bucket4j's Redis integration) if it ever runs
 * multiple instances behind a load balancer, since each instance would
 * otherwise enforce its own independent limit.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private static final int GENERAL_CAPACITY = 120;
    private static final int FINANCIAL_CAPACITY = 10;

    private static final List<String> FINANCIAL_MUTATION_PATTERNS = List.of(
            "/api/v1/users/me/deposits",
            "/api/v1/vaults/*/deposits",
            "/api/v1/vaults/*/withdrawals",
            "/api/v1/transfers",
            "/api/v1/susu/contributions/*"
    );

    private final Map<String, Bucket> generalBuckets = new ConcurrentHashMap<>();
    private final Map<String, Bucket> financialBuckets = new ConcurrentHashMap<>();
    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String key = resolveKey(request);

        Bucket generalBucket = generalBuckets.computeIfAbsent(key, k -> newBucket(GENERAL_CAPACITY));
        if (!generalBucket.tryConsume(1)) {
            writeTooManyRequests(response);
            return;
        }

        if (isFinancialMutation(request)) {
            Bucket financialBucket =
                    financialBuckets.computeIfAbsent(key, k -> newBucket(FINANCIAL_CAPACITY));
            if (!financialBucket.tryConsume(1)) {
                writeTooManyRequests(response);
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private static Bucket newBucket(int capacityPerMinute) {
        return Bucket.builder()
                .addLimit(Bandwidth.simple(capacityPerMinute, Duration.ofMinutes(1)))
                .build();
    }

    private boolean isFinancialMutation(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return false;
        }
        String uri = request.getRequestURI();
        return FINANCIAL_MUTATION_PATTERNS.stream().anyMatch(pattern -> pathMatcher.match(pattern, uri));
    }

    private String resolveKey(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() != null) {
            return "user:" + authentication.getPrincipal();
        }
        return "ip:" + resolveClientIp(request);
    }

    private static String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private void writeTooManyRequests(HttpServletResponse response) throws IOException {
        response.setStatus(429);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ErrorResponse body = ErrorResponse.of(
                ErrorCode.RATE_LIMIT_EXCEEDED,
                "Too many requests. Please slow down and try again shortly.",
                Map.of(),
                CorrelationContext.get()
        );
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
