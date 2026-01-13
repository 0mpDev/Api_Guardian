package com.patniom.api_guardian.ratelimit;

import com.patniom.api_guardian.audit.AsyncAuditLogService;
import com.patniom.api_guardian.kafka.KafkaProducerService;
import com.patniom.api_guardian.kafka.events.ApiKeyUsageEvent;
import com.patniom.api_guardian.kafka.events.ApiRequestEvent;
import com.patniom.api_guardian.metrics.MetricsService;
import com.patniom.api_guardian.security.apikey.ApiKey;
import com.patniom.api_guardian.util.ClientIpUtil;
import io.micrometer.core.instrument.Timer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Component
public class RateLimitingFilter extends OncePerRequestFilter {

    @Autowired
    private RateLimiterService rateLimiterService;

    @Autowired
    private AbuseDetectionService abuseDetectionService;

    @Autowired
    private AsyncAuditLogService auditLogService; // ✅ CHANGED to async

    @Autowired
    private KafkaProducerService kafkaProducerService;

    @Autowired
    private MetricsService metricsService; // ✅ NEW

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String requestId = UUID.randomUUID().toString();
        Timer.Sample timerSample = metricsService.startTimer(); // ✅ Start timer
        long startTime = System.currentTimeMillis();

        String identifier = resolveIdentifier(request);
        RateLimitConfig config = resolveRateLimitConfig(request);

        String decision;
        int statusCode;

        // 🚫 STEP 1: Check if banned
        if (abuseDetectionService.isBanned(identifier)) {
            decision = "BANNED";
            statusCode = 403;

            auditLogService.log(decision, request, identifier);
            sendRateLimitResponse(response, statusCode,
                    "You are temporarily banned due to abuse", null);

            // ✅ Record metrics
            metricsService.recordRequest(decision, config.getTier().name(),
                    request.getRequestURI());
            metricsService.stopTimer(timerSample);

            sendApiRequestEventToKafka(request, identifier, decision, statusCode,
                    config, startTime, requestId);
            return;
        }

        // ⏱ STEP 2: Check rate limit
        boolean allowed = rateLimiterService.allowRequest(identifier, config);

        if (!allowed) {
            decision = "RATE_LIMIT";
            statusCode = 429;

            abuseDetectionService.recordViolation(identifier);
            long retryAfter = rateLimiterService.getRetryAfterSeconds(identifier, config);

            auditLogService.log(decision, request, identifier);
            sendRateLimitResponse(response, statusCode,
                    "Rate limit exceeded. Please try again later.", retryAfter);

            // ✅ Record metrics
            metricsService.recordRequest(decision, config.getTier().name(),
                    request.getRequestURI());
            metricsService.stopTimer(timerSample);

            sendApiRequestEventToKafka(request, identifier, decision, statusCode,
                    config, startTime, requestId);
            return;
        }

        // ✅ STEP 3: Request allowed
        decision = "ALLOW";
        statusCode = 200;

        auditLogService.log(decision, request, identifier);
        setRateLimitHeaders(response, identifier, config);

        trackApiKeyUsageViaKafka(request);

        filterChain.doFilter(request, response);

        // ✅ Record metrics AFTER response
        metricsService.recordRequest(decision, config.getTier().name(),
                request.getRequestURI());
        metricsService.stopTimer(timerSample);

        sendApiRequestEventToKafka(request, identifier, decision, statusCode,
                config, startTime, requestId);
    }

    private void sendApiRequestEventToKafka(HttpServletRequest request,
                                            String identifier,
                                            String decision,
                                            int statusCode,
                                            RateLimitConfig config,
                                            long startTime,
                                            String requestId) {
        try {
            ApiRequestEvent event = ApiRequestEvent.builder()
                    .requestId(requestId)
                    .identifier(identifier)
                    .endpoint(request.getRequestURI())
                    .httpMethod(request.getMethod())
                    .decision(decision)
                    .tier(config.getTier().name())
                    .statusCode(statusCode)
                    .responseTimeMs(System.currentTimeMillis() - startTime)
                    .userAgent(request.getHeader("User-Agent"))
                    .ipAddress(ClientIpUtil.getClientIp(request))
                    .timestamp(LocalDateTime.now())
                    .build();

            kafkaProducerService.sendApiRequestEvent(event);
        } catch (Exception e) {
            log.error("Failed to send API request event to Kafka: {}", e.getMessage());
        }
    }

    private void trackApiKeyUsageViaKafka(HttpServletRequest request) {
        try {
            ApiKey apiKey = (ApiKey) request.getAttribute("API_KEY_OBJ");
            if (apiKey != null) {
                ApiKeyUsageEvent event = ApiKeyUsageEvent.builder()
                        .apiKeyId(apiKey.getId())
                        .userId(apiKey.getUserId())
                        .endpoint(request.getRequestURI())
                        .success(true)
                        .tier(apiKey.getTier().name())
                        .timestamp(LocalDateTime.now())
                        .build();

                kafkaProducerService.sendApiKeyUsageEvent(event);
            }
        } catch (Exception e) {
            log.error("Failed to send API key usage event: {}", e.getMessage());
        }
    }

    private String resolveIdentifier(HttpServletRequest request) {
        if (request.getAttribute("USER_ID") != null) {
            return "USER:" + request.getAttribute("USER_ID");
        }
        if (request.getAttribute("API_KEY_ID") != null) {
            return "KEY:" + request.getAttribute("API_KEY_ID");
        }
        return "IP:" + ClientIpUtil.getClientIp(request);
    }

    private RateLimitConfig resolveRateLimitConfig(HttpServletRequest request) {
        ApiKey.RateLimitTier tier = (ApiKey.RateLimitTier)
                request.getAttribute("API_KEY_TIER");

        if (tier != null) {
            return RateLimitConfig.builder()
                    .tier(tier)
                    .capacity(tier.getRequestsPerMinute().intValue())
                    .refillIntervalMs(60_000L)
                    .build();
        }

        return RateLimitConfig.builder()
                .tier(ApiKey.RateLimitTier.FREE)
                .capacity(100)
                .refillIntervalMs(60_000L)
                .build();
    }

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
}