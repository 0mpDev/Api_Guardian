package com.patniom.api_guardian.ratelimit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class AbuseDetectionService {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    // ========== Configuration (can be moved to application.properties) ==========
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

        // Calculate and apply ban based on violation count
        long banSeconds = calculateBanTime(violations);

        if (banSeconds > 0) {
            applyBan(identifier, banSeconds, violations);
        }
    }

    /**
     * Apply a ban to an identifier
     */
    private void applyBan(String identifier, long banSeconds, Long violations) {
        String banKey = "ban:" + identifier;

        redisTemplate.opsForValue().set(banKey, "1", banSeconds, TimeUnit.SECONDS);

        log.error("🚫 BAN APPLIED to {} for {} seconds (violations: {})",
                identifier, banSeconds, violations);
    }

    /**
     * Calculate ban duration based on violation count
     */
    private long calculateBanTime(Long violations) {
        if (violations == null) {
            return 0;
        }

        if (violations >= VIOLATION_THRESHOLD_SEVERE) {
            return BAN_DURATION_SEVERE; // 30 min
        }

        if (violations >= VIOLATION_THRESHOLD_MODERATE) {
            return BAN_DURATION_MODERATE; // 5 min
        }

        if (violations >= VIOLATION_THRESHOLD_MINOR) {
            return BAN_DURATION_MINOR; // 1 min
        }

        return 0; // No ban yet
    }

    /**
     * Get current violation count for an identifier
     */
    public long getViolationCount(String identifier) {
        String violationKey = "abuse:" + identifier + ":violations";
        Object violations = redisTemplate.opsForValue().get(violationKey);

        if (violations == null) {
            return 0;
        }

        return violations instanceof Integer ? (Integer) violations :
                Long.parseLong(violations.toString());
    }

    /**
     * Get remaining ban time in seconds
     */
    public long getRemainingBanTime(String identifier) {
        String banKey = "ban:" + identifier;
        Long ttl = redisTemplate.getExpire(banKey, TimeUnit.SECONDS);
        return ttl != null && ttl > 0 ? ttl : 0;
    }

    /**
     * Manually clear ban (admin feature)
     */
    public void clearBan(String identifier) {
        String banKey = "ban:" + identifier;
        String violationKey = "abuse:" + identifier + ":violations";

        redisTemplate.delete(banKey);
        redisTemplate.delete(violationKey);

        log.info("✅ Ban cleared for: {}", identifier);
    }

    /**
     * Manually clear all bans (testing/admin feature)
     */
    public void clearAllBans() {
        // Note: In production, use SCAN instead of KEYS for large datasets
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