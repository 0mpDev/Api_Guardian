package com.patniom.api_guardian.ratelimit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RateLimiterService {

    private static final int CAPACITY = 100;
    private static final long REFILL_INTERVAL_MS = 60_000;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private final DefaultRedisScript<Long> rateLimitScript;

    public RateLimiterService() {
        rateLimitScript = new DefaultRedisScript<>();
        rateLimitScript.setResultType(Long.class);
        rateLimitScript.setScriptText("""
            local tokens = tonumber(redis.call("GET", KEYS[1]))
            local lastRefill = tonumber(redis.call("GET", KEYS[2]))
            local capacity = tonumber(ARGV[1])
            local refillInterval = tonumber(ARGV[2])
            local now = tonumber(ARGV[3])

            if tokens == nil or lastRefill == nil then
                redis.call("SET", KEYS[1], capacity - 1)
                redis.call("SET", KEYS[2], now)
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
    }

    public boolean allowRequest(String identifier) {

        String tokenKey = "bucket:" + identifier + ":tokens";
        String timeKey  = "bucket:" + identifier + ":lastRefill";

        Long result = redisTemplate.execute(
                rateLimitScript,
                List.of(tokenKey, timeKey),
                CAPACITY,
                REFILL_INTERVAL_MS,
                System.currentTimeMillis()
        );

        return result != null && result == 1;
    }
}

