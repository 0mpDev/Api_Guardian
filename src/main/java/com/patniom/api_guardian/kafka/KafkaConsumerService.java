package com.patniom.api_guardian.kafka;

import com.patniom.api_guardian.kafka.events.*;
import com.patniom.api_guardian.security.apikey.ApiKey;
import com.patniom.api_guardian.security.apikey.ApiKeyRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
public class KafkaConsumerService {

    @Autowired
    private ApiKeyRepository apiKeyRepository;

    // In-memory cache for batching API key usage updates
    private final Map<String, UsageStats> usageCache = new HashMap<>();

    /**
     * Consumer for API Request events
     * Logs all requests for analytics
     */
    @KafkaListener(
            topics = "${kafka.topics.api-requests}",
            groupId = "api-requests-analytics"
    )
    public void consumeApiRequestEvent(ApiRequestEvent event, Acknowledgment ack) {
        try {
            log.debug("📥 Received API request event: {} -> {} ({})",
                    event.getIdentifier(), event.getEndpoint(), event.getDecision());

            // Here you can:
            // 1. Store in a time-series database (like InfluxDB or Prometheus)
            // 2. Send to monitoring system
            // 3. Aggregate for dashboard

            ack.acknowledge();
        } catch (Exception e) {
            log.error("Error processing API request event: {}", e.getMessage());
        }
    }

    /**
     * Consumer for Rate Limit Violation events
     * Tracks violations for security analytics
     */
    @KafkaListener(
            topics = "${kafka.topics.rate-limit-violations}",
            groupId = "violations-tracker"
    )
    public void consumeRateLimitViolation(RateLimitViolationEvent event, Acknowledgment ack) {
        try {
            log.warn("⚠️ Received violation event: {} (count: {})",
                    event.getIdentifier(), event.getViolationCount());

            // Here you can:
            // 1. Alert security team if violations exceed threshold
            // 2. Store in security log database
            // 3. Update monitoring dashboard

            ack.acknowledge();
        } catch (Exception e) {
            log.error("Error processing violation event: {}", e.getMessage());
        }
    }

    /**
     * Consumer for API Key Usage events
     * CRITICAL: This solves the MongoDB hot-path issue!
     * Batch updates MongoDB instead of writing on every request
     */
    @KafkaListener(
            topics = "${kafka.topics.api-key-usage}",
            groupId = "api-key-usage-tracker",
            concurrency = "2"
    )
    public void consumeApiKeyUsage(ApiKeyUsageEvent event, Acknowledgment ack) {
        try {
            log.debug("📊 Received API key usage: {} for {}",
                    event.getApiKeyId(), event.getEndpoint());

            // Accumulate usage in memory
            synchronized (usageCache) {
                UsageStats stats = usageCache.computeIfAbsent(
                        event.getApiKeyId(),
                        k -> new UsageStats()
                );

                stats.totalRequests++;
                if (event.isSuccess()) {
                    stats.successfulRequests++;
                } else {
                    stats.failedRequests++;
                }
                stats.lastUsedAt = event.getTimestamp();

                // Batch flush to MongoDB every 100 requests
                if (stats.totalRequests % 100 == 0) {
                    flushUsageToMongoDB(event.getApiKeyId(), stats);
                    usageCache.remove(event.getApiKeyId());
                }
            }

            ack.acknowledge();
        } catch (Exception e) {
            log.error("Error processing API key usage event: {}", e.getMessage());
        }
    }

    /**
     * Consumer for Ban events
     * Logs and alerts on bans
     */
    @KafkaListener(
            topics = "${kafka.topics.ban-events}",
            groupId = "ban-alerts"
    )
    public void consumeBanEvent(BanEvent event, Acknowledgment ack) {
        try {
            log.error("🚫 Received ban event: {} ({} violations, banned for {}s)",
                    event.getIdentifier(),
                    event.getViolationCount(),
                    event.getBanDurationSeconds());

            // Here you can:
            // 1. Send alert to security team
            // 2. Store in security incidents database
            // 3. Trigger automated response (e.g., block IP at firewall)

            ack.acknowledge();
        } catch (Exception e) {
            log.error("Error processing ban event: {}", e.getMessage());
        }
    }

    /**
     * Flush accumulated usage stats to MongoDB
     */
    private void flushUsageToMongoDB(String apiKeyId, UsageStats stats) {
        try {
            Optional<ApiKey> apiKeyOpt = apiKeyRepository.findById(apiKeyId);

            if (apiKeyOpt.isPresent()) {
                ApiKey apiKey = apiKeyOpt.get();

                // Update usage statistics
                apiKey.setTotalRequests(
                        (apiKey.getTotalRequests() != null ? apiKey.getTotalRequests() : 0L)
                                + stats.totalRequests
                );
                apiKey.setSuccessfulRequests(
                        (apiKey.getSuccessfulRequests() != null ? apiKey.getSuccessfulRequests() : 0L)
                                + stats.successfulRequests
                );
                apiKey.setFailedRequests(
                        (apiKey.getFailedRequests() != null ? apiKey.getFailedRequests() : 0L)
                                + stats.failedRequests
                );
                apiKey.setLastUsedAt(stats.lastUsedAt);

                apiKeyRepository.save(apiKey);

                log.info("💾 Flushed usage stats to MongoDB for API key: {} (+{} requests)",
                        apiKeyId, stats.totalRequests);
            }
        } catch (Exception e) {
            log.error("Failed to flush usage stats to MongoDB: {}", e.getMessage());
        }
    }

    /**
     * Scheduled job to flush remaining usage stats
     * Runs every 5 minutes to ensure data is eventually consistent
     */
    @org.springframework.scheduling.annotation.Scheduled(fixedRate = 300000) // 5 minutes
    public void scheduledFlush() {
        log.info("🔄 Running scheduled flush of API key usage stats...");

        synchronized (usageCache) {
            usageCache.forEach((apiKeyId, stats) -> {
                flushUsageToMongoDB(apiKeyId, stats);
            });
            usageCache.clear();
        }

        log.info("✅ Scheduled flush complete");
    }

    /**
     * Internal class to accumulate usage statistics
     */
    private static class UsageStats {
        long totalRequests = 0;
        long successfulRequests = 0;
        long failedRequests = 0;
        LocalDateTime lastUsedAt;
    }
}