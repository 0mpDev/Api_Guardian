package com.patniom.api_guardian.kafka;

import com.patniom.api_guardian.kafka.events.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
public class KafkaProducerService {

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${kafka.topics.api-requests}")
    private String apiRequestsTopic;

    @Value("${kafka.topics.rate-limit-violations}")
    private String rateLimitViolationsTopic;

    @Value("${kafka.topics.api-key-usage}")
    private String apiKeyUsageTopic;

    @Value("${kafka.topics.ban-events}")
    private String banEventsTopic;

    /**
     * Send API request event (async - doesn't block the request)
     */
    @Async
    public void sendApiRequestEvent(ApiRequestEvent event) {
        try {
            CompletableFuture<SendResult<String, Object>> future =
                    kafkaTemplate.send(apiRequestsTopic, event.getIdentifier(), event);

            future.whenComplete((result, ex) -> {
                if (ex == null) {
                    log.debug("📤 API request event sent: {} -> {}",
                            event.getIdentifier(), event.getEndpoint());
                } else {
                    log.error("❌ Failed to send API request event: {}", ex.getMessage());
                }
            });
        } catch (Exception e) {
            log.error("❌ Error sending API request event: {}", e.getMessage());
        }
    }

    /**
     * Send rate limit violation event
     */
    @Async
    public void sendRateLimitViolationEvent(RateLimitViolationEvent event) {
        try {
            CompletableFuture<SendResult<String, Object>> future =
                    kafkaTemplate.send(rateLimitViolationsTopic, event.getIdentifier(), event);

            future.whenComplete((result, ex) -> {
                if (ex == null) {
                    log.warn("⚠️ Rate limit violation event sent: {}", event.getIdentifier());
                } else {
                    log.error("❌ Failed to send violation event: {}", ex.getMessage());
                }
            });
        } catch (Exception e) {
            log.error("❌ Error sending violation event: {}", e.getMessage());
        }
    }

    /**
     * Send API key usage event (for batch MongoDB updates)
     */
    @Async
    public void sendApiKeyUsageEvent(ApiKeyUsageEvent event) {
        try {
            CompletableFuture<SendResult<String, Object>> future =
                    kafkaTemplate.send(apiKeyUsageTopic, event.getApiKeyId(), event);

            future.whenComplete((result, ex) -> {
                if (ex == null) {
                    log.debug("📊 API key usage event sent: {}", event.getApiKeyId());
                } else {
                    log.error("❌ Failed to send usage event: {}", ex.getMessage());
                }
            });
        } catch (Exception e) {
            log.error("❌ Error sending usage event: {}", e.getMessage());
        }
    }

    /**
     * Send ban event
     */
    @Async
    public void sendBanEvent(BanEvent event) {
        try {
            CompletableFuture<SendResult<String, Object>> future =
                    kafkaTemplate.send(banEventsTopic, event.getIdentifier(), event);

            future.whenComplete((result, ex) -> {
                if (ex == null) {
                    log.error("🚫 Ban event sent: {} (violations: {})",
                            event.getIdentifier(), event.getViolationCount());
                } else {
                    log.error("❌ Failed to send ban event: {}", ex.getMessage());
                }
            });
        } catch (Exception e) {
            log.error("❌ Error sending ban event: {}", e.getMessage());
        }
    }
}