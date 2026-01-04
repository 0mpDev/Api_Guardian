package com.patniom.api_guardian.ratelimit;

import com.patniom.api_guardian.audit.AuditLogService;
import com.patniom.api_guardian.security.apikey.ApiKey;
import com.patniom.api_guardian.security.apikey.ApiKeyService;
import com.patniom.api_guardian.util.ClientIpUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@Component
public class RateLimitingFilter extends OncePerRequestFilter {

    @Autowired
    private RateLimiterService rateLimiterService;

    @Autowired
    private AbuseDetectionService abuseDetectionService;

    @Autowired
    private ApiKeyService apiKeyService;

    @Autowired
    private AuditLogService auditLogService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String identifier = resolveIdentifier(request);
        RateLimitConfig config = resolveRateLimitConfig(request);

        // 🚫 STEP 1: Banned
        if (abuseDetectionService.isBanned(identifier)) {

            auditLogService.log("BANNED", request, identifier);

            sendRateLimitResponse(
                    response,
                    403,
                    "You are temporarily banned due to abuse",
                    null
            );
            return;
        }

        // ⏱ STEP 2: Rate limit
        boolean allowed = rateLimiterService.allowRequest(identifier, config);

        if (!allowed) {
            abuseDetectionService.recordViolation(identifier);

            long retryAfter =
                    rateLimiterService.getRetryAfterSeconds(identifier, config);

            auditLogService.log("RATE_LIMIT", request, identifier);

            sendRateLimitResponse(
                    response,
                    429,
                    "Rate limit exceeded. Please try again later.",
                    retryAfter
            );
            return;
        }

        // ✅ STEP 3: Allowed
        auditLogService.log("ALLOW", request, identifier);

        setRateLimitHeaders(response, identifier, config);
        filterChain.doFilter(request, response);
    }



//    @Override
//    protected void doFilterInternal(HttpServletRequest request,
//                                    HttpServletResponse response,
//                                    FilterChain filterChain)
//            throws ServletException, IOException {
//
//        String identifier = resolveIdentifier(request);
//        RateLimitConfig config = resolveRateLimitConfig(request);
//
//        log.debug("🔍 Rate limit check - Identifier: {}, Tier: {}, Limit: {}/min",
//                identifier, config.getTier(), config.getCapacity());
//
//        // 🚫 STEP 1: Check if identifier is banned
//        if (abuseDetectionService.isBanned(identifier)) {
//            log.warn("Banned identifier attempted access: {}", identifier);
//            request.setAttribute("AUDIT_DECISION", "BANNED");
//            sendRateLimitResponse(response, 403, "You are temporarily banned", null);
//            return;
//        }
//
//        // ⏱ STEP 2: Apply rate limiting based on tier
//        boolean allowed = rateLimiterService.allowRequest(identifier, config);
//
//        if (!allowed) {
//            abuseDetectionService.recordViolation(identifier);
//
//            // Get remaining time until reset
//            long retryAfter = rateLimiterService.getRetryAfterSeconds(identifier, config);
//
//            log.warn("Rate limit exceeded for: {}, tier: {}",
//                    identifier, config.getTier());
//
//            request.setAttribute("AUDIT_DECISION", "RATE_LIMIT");
//            sendRateLimitResponse(response, 429,
//                    "Rate limit exceeded. Please try again later.",
//                    retryAfter);
//            return;
//        }
//
//        // ✅ STEP 3: Request allowed - set rate limit headers
//        setRateLimitHeaders(response, identifier, config);
//
//        // TODO: PERFORMANCE - Move usage tracking to Redis + batch flush to MongoDB
//        // Currently disabled to prevent MongoDB writes on every request (bottleneck)
//        // trackApiKeyUsage(request, true);
//
//        request.setAttribute("AUDIT_DECISION", "ALLOW");
//
//        filterChain.doFilter(request, response);
//    }

    /**
     * Resolve identifier based on authentication method
     */
    private String resolveIdentifier(HttpServletRequest request) {
        // Priority 1: User ID from JWT
        if (request.getAttribute("USER_ID") != null) {
            return "USER:" + request.getAttribute("USER_ID");
        }

        // Priority 2: API Key ID
        if (request.getAttribute("API_KEY_ID") != null) {
            return "KEY:" + request.getAttribute("API_KEY_ID");
        }

        // Priority 3: IP Address (fallback)
        return "IP:" + ClientIpUtil.getClientIp(request);
    }

    /**
     * Resolve rate limit configuration based on API key tier
     */
    private RateLimitConfig resolveRateLimitConfig(HttpServletRequest request) {
        ApiKey.RateLimitTier tier = (ApiKey.RateLimitTier)
                request.getAttribute("API_KEY_TIER");

        if (tier != null) {
            // Use tier-based limits
            return RateLimitConfig.builder()
                    .tier(tier)
                    .capacity(tier.getRequestsPerMinute().intValue())
                    .refillIntervalMs(60_000L) // 1 minute
                    .build();
        }

        // Default limits for non-API-key requests (IP-based)
        return RateLimitConfig.builder()
                .tier(ApiKey.RateLimitTier.FREE)
                .capacity(100)
                .refillIntervalMs(60_000L)
                .build();
    }

    /**
     * Set rate limit info headers
     */
    private void setRateLimitHeaders(HttpServletResponse response,
                                     String identifier,
                                     RateLimitConfig config) {

        long remaining = rateLimiterService.getRemainingTokens(identifier, config);
        long resetTime = rateLimiterService.getResetTime(identifier, config);

        response.setHeader("X-RateLimit-Limit", String.valueOf(config.getCapacity()));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(remaining));
        response.setHeader("X-RateLimit-Reset", String.valueOf(resetTime));
        response.setHeader("X-RateLimit-Tier", config.getTier().name());
    }

    /**
     * Send standardized rate limit response
     */
    private void sendRateLimitResponse(HttpServletResponse response,
                                       int status,
                                       String message,
                                       Long retryAfter) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");

        if (retryAfter != null) {
            response.setHeader("Retry-After", String.valueOf(retryAfter));
        }

        String jsonResponse = String.format("""
            {
                "error": "%s",
                "message": "%s",
                "retryAfter": %s
            }
            """,
                status == 429 ? "Too Many Requests" : "Forbidden",
                message,
                retryAfter != null ? retryAfter : "null"
        );

        response.getWriter().write(jsonResponse);
    }

    /**
     * Track API key usage for analytics
     *
     * TODO: PERFORMANCE FIX NEEDED
     * This method writes to MongoDB on EVERY request, causing a bottleneck.
     * Solutions:
     * 1. Use Redis counters, batch flush to MongoDB every 5 minutes
     * 2. Use Kafka to stream usage events asynchronously
     * 3. Use in-memory counters with scheduled MongoDB writes
     */
    /*
    private void trackApiKeyUsage(HttpServletRequest request, boolean success) {
        ApiKey apiKey = (ApiKey) request.getAttribute("API_KEY_OBJ");
        if (apiKey != null) {
            apiKeyService.incrementUsage(apiKey, success);
        }
    }
    */
}