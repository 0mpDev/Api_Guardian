package com.patniom.api_guardian.ratelimit;

import com.patniom.api_guardian.kafka.KafkaProducerService;
import com.patniom.api_guardian.kafka.events.BanEvent;
import com.patniom.api_guardian.kafka.events.RateLimitViolationEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class AbuseDetectionService {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private KafkaProducerService kafkaProducerService;

    // Configuration (can be moved to @ConfigurationProperties)
    private static final int VIOLATION_WINDOW_SECONDS = 300; // 5 minutes
    private static final int VIOLATION_THRESHOLD_MINOR = 3;
    private static final int VIOLATION_THRESHOLD_MODERATE = 5;
    private static final int VIOLATION_THRESHOLD_SEVERE = 10;

    private static final long BAN_DURATION_MINOR = 60;        // 1 minute
    private static final long BAN_DURATION_MODERATE = 300;    // 5 minutes
    private static final long BAN_DURATION_SEVERE = 1800;     // 30 minutes

    /**
     * Check if identifier is currently banned
     */
    public boolean isBanned(String identifier) {
        Boolean banned = redisTemplate.hasKey("ban:" + identifier);

        if (Boolean.TRUE.equals(banned)) {
            log.warn("⛔ Banned identifier attempted access: {}", identifier);
        }

        return Boolean.TRUE.equals(banned);
    }

    /**
     * Record a rate limit violation and apply ban if threshold exceeded
     * NOW SENDS EVENTS TO KAFKA!
     */
    public void recordViolation(String identifier) {
        String violationKey = "abuse:" + identifier + ":violations";

        // Increment violation count
        Long violations = redisTemplate.opsForValue().increment(violationKey);

        // Set TTL on first violation (sliding window)
        if (violations == 1) {
            redisTemplate.expire(violationKey, VIOLATION_WINDOW_SECONDS, TimeUnit.SECONDS);
            log.debug("First violation recorded for: {}", identifier);
        }

        log.warn("⚠️ Rate limit violation #{} for: {}", violations, identifier);

        // Send violation event to Kafka
        sendViolationEventToKafka(identifier, violations);

        // Calculate and apply ban based on violation count
        long banSeconds = calculateBanTime(violations);

        if (banSeconds > 0) {
            applyBan(identifier, banSeconds, violations);
        }
    }

    /**
     * Send violation event to Kafka
     */
    private void sendViolationEventToKafka(String identifier, Long violations) {
        try {
            RateLimitViolationEvent event = RateLimitViolationEvent.builder()
                    .identifier(identifier)
                    .endpoint("N/A") // Can be enriched if needed
                    .tier("N/A")
                    .violationCount(violations.intValue())
                    .retryAfterSeconds(calculateBanTime(violations))
                    .timestamp(LocalDateTime.now())
                    .build();

            kafkaProducerService.sendRateLimitViolationEvent(event);
        } catch (Exception e) {
            log.error("Failed to send violation event to Kafka: {}", e.getMessage());
        }
    }

    /**
     * Apply a ban to an identifier and send ban event to Kafka
     */
    private void applyBan(String identifier, long banSeconds, Long violations) {
        String banKey = "ban:" + identifier;

        redisTemplate.opsForValue().set(banKey, "1", banSeconds, TimeUnit.SECONDS);

        log.error("🚫 BAN APPLIED to {} for {} seconds (violations: {})",
                identifier, banSeconds, violations);

        // Send ban event to Kafka
        sendBanEventToKafka(identifier, violations, banSeconds);
    }

    /**
     * Send ban event to Kafka
     */
    private void sendBanEventToKafka(String identifier, Long violations, long banSeconds) {
        try {
            LocalDateTime now = LocalDateTime.now();
            BanEvent event = BanEvent.builder()
                    .identifier(identifier)
                    .reason("Exceeded rate limit " + violations + " times")
                    .violationCount(violations.intValue())
                    .banDurationSeconds(banSeconds)
                    .bannedAt(now)
                    .expiresAt(now.plusSeconds(banSeconds))
                    .build();

            kafkaProducerService.sendBanEvent(event);
        } catch (Exception e) {
            log.error("Failed to send ban event to Kafka: {}", e.getMessage());
        }
    }

    private long calculateBanTime(Long violations) {
        if (violations == null) {
            return 0;
        }

        if (violations >= VIOLATION_THRESHOLD_SEVERE) {
            return BAN_DURATION_SEVERE;
        }

        if (violations >= VIOLATION_THRESHOLD_MODERATE) {
            return BAN_DURATION_MODERATE;
        }

        if (violations >= VIOLATION_THRESHOLD_MINOR) {
            return BAN_DURATION_MINOR;
        }

        return 0;
    }

    public long getViolationCount(String identifier) {
        String violationKey = "abuse:" + identifier + ":violations";
        Object violations = redisTemplate.opsForValue().get(violationKey);

        if (violations == null) {
            return 0;
        }

        return violations instanceof Integer ? (Integer) violations :
                Long.parseLong(violations.toString());
    }

    public long getRemainingBanTime(String identifier) {
        String banKey = "ban:" + identifier;
        Long ttl = redisTemplate.getExpire(banKey, TimeUnit.SECONDS);
        return ttl != null && ttl > 0 ? ttl : 0;
    }

    public void clearBan(String identifier) {
        String banKey = "ban:" + identifier;
        String violationKey = "abuse:" + identifier + ":violations";

        redisTemplate.delete(banKey);
        redisTemplate.delete(violationKey);

        log.info("✅ Ban cleared for: {}", identifier);
    }

    public void clearAllBans() {
        // PROD WARNING: Use SCAN instead of KEYS
        var banKeys = redisTemplate.keys("ban:*");
        var violationKeys = redisTemplate.keys("abuse:*");

        if (banKeys != null && !banKeys.isEmpty()) {
            redisTemplate.delete(banKeys);
            log.info("Cleared {} ban(s)", banKeys.size());
        }

        if (violationKeys != null && !violationKeys.isEmpty()) {
            redisTemplate.delete(violationKeys);
            log.info("Cleared {} violation record(s)", violationKeys.size());
        }
    }
}