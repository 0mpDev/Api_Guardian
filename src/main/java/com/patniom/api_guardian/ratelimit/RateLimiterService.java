package com.patniom.api_guardian.ratelimit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class RateLimiterService {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private final DefaultRedisScript<Long> tokenBucketScript;
    private final DefaultRedisScript<Long> slidingWindowScript;

    public RateLimiterService() {
        // Token Bucket Algorithm (your existing implementation)
        tokenBucketScript = new DefaultRedisScript<>();
        tokenBucketScript.setResultType(Long.class);
        tokenBucketScript.setScriptText("""
            local tokens = tonumber(redis.call("GET", KEYS[1]))
            local lastRefill = tonumber(redis.call("GET", KEYS[2]))
            local capacity = tonumber(ARGV[1])
            local refillInterval = tonumber(ARGV[2])
            local now = tonumber(ARGV[3])

            if tokens == nil or lastRefill == nil then
                redis.call("SET", KEYS[1], capacity - 1)
                redis.call("SET", KEYS[2], now)
                redis.call("EXPIRE", KEYS[1], 3600)
                redis.call("EXPIRE", KEYS[2], 3600)
                return 1
            end

            if now - lastRefill >= refillInterval then
                tokens = capacity
                redis.call("SET", KEYS[2], now)
            end

            if tokens > 0 then
                redis.call("SET", KEYS[1], tokens - 1)
                return 1
            end

            return 0
        """);

        // Sliding Window Algorithm (for hourly/daily limits)
        slidingWindowScript = new DefaultRedisScript<>();
        slidingWindowScript.setResultType(Long.class);
        slidingWindowScript.setScriptText("""
            local key = KEYS[1]
            local limit = tonumber(ARGV[1])
            local window = tonumber(ARGV[2])
            local now = tonumber(ARGV[3])
            local clearBefore = now - window

            redis.call('ZREMRANGEBYSCORE', key, 0, clearBefore)
            local count = redis.call('ZCARD', key)

            if count < limit then
                redis.call('ZADD', key, now, now)
                redis.call('EXPIRE', key, window)
                return 1
            end

            return 0
        """);
    }

    /**
     * Check if request is allowed (Token Bucket)
     */
    public boolean allowRequest(String identifier, RateLimitConfig config) {
        String tokenKey = "bucket:" + identifier + ":tokens";
        String timeKey = "bucket:" + identifier + ":lastRefill";

        Long result = redisTemplate.execute(
                tokenBucketScript,
                List.of(tokenKey, timeKey),
                config.getCapacity(),
                config.getRefillIntervalMs(),
                System.currentTimeMillis()
        );

        return result != null && result == 1;
    }

    /**
     * Check if request is allowed (Sliding Window)
     * Useful for hourly/daily limits
     */
    public boolean allowRequestSlidingWindow(String identifier,
                                             long limit,
                                             long windowSeconds) {
        String key = "sliding:" + identifier + ":" + windowSeconds;
        long now = Instant.now().getEpochSecond();

        Long result = redisTemplate.execute(
                slidingWindowScript,
                List.of(key),
                limit,
                windowSeconds,
                now
        );

        return result != null && result == 1;
    }

    /**
     * Get remaining tokens for an identifier
     */
    public long getRemainingTokens(String identifier, RateLimitConfig config) {
        String tokenKey = "bucket:" + identifier + ":tokens";
        Object tokens = redisTemplate.opsForValue().get(tokenKey);

        if (tokens == null) {
            return config.getCapacity();
        }

        return tokens instanceof Integer ? (Integer) tokens :
                Long.parseLong(tokens.toString());
    }

    /**
     * Get time until rate limit resets (in seconds)
     */
    public long getRetryAfterSeconds(String identifier, RateLimitConfig config) {
        String timeKey = "bucket:" + identifier + ":lastRefill";
        Object lastRefillObj = redisTemplate.opsForValue().get(timeKey);

        if (lastRefillObj == null) {
            return 0;
        }

        long lastRefill = lastRefillObj instanceof Long ? (Long) lastRefillObj :
                Long.parseLong(lastRefillObj.toString());
        long now = System.currentTimeMillis();
        long elapsed = now - lastRefill;
        long remaining = config.getRefillIntervalMs() - elapsed;

        return Math.max(0, remaining / 1000);
    }

    /**
     * Get reset timestamp (Unix epoch)
     */
    public long getResetTime(String identifier, RateLimitConfig config) {
        String timeKey = "bucket:" + identifier + ":lastRefill";
        Object lastRefillObj = redisTemplate.opsForValue().get(timeKey);

        if (lastRefillObj == null) {
            return Instant.now().getEpochSecond() +
                    (config.getRefillIntervalMs() / 1000);
        }

        long lastRefill = lastRefillObj instanceof Long ? (Long) lastRefillObj :
                Long.parseLong(lastRefillObj.toString());

        return (lastRefill + config.getRefillIntervalMs()) / 1000;
    }

    /**
     * Manually reset rate limit for an identifier (admin feature)
     */
    public void resetRateLimit(String identifier) {
        String tokenKey = "bucket:" + identifier + ":tokens";
        String timeKey = "bucket:" + identifier + ":lastRefill";

        redisTemplate.delete(tokenKey);
        redisTemplate.delete(timeKey);

        log.info("Rate limit reset for identifier: {}", identifier);
    }

    /**
     * Get current request count in sliding window
     */
    public long getCurrentRequestCount(String identifier, long windowSeconds) {
        String key = "sliding:" + identifier + ":" + windowSeconds;
        Long count = redisTemplate.opsForZSet().zCard(key);
        return count != null ? count : 0;
    }
}