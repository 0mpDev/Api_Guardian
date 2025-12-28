package com.patniom.api_guardian.ratelimit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class AbuseDetectionService {
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    public boolean isBanned(String identifier) {
        return Boolean.TRUE.equals(redisTemplate.hasKey("ban:" + identifier));
    }

    public void recordViolation(String identifier) {

        String violationKey = "abuse:" + identifier + ":violations";

        Long violations = redisTemplate.opsForValue().increment(violationKey);

        long banSeconds = calculateBanTime(violations);

        if (banSeconds > 0) {
            redisTemplate.opsForValue()
                    .set("ban:" + identifier, "1", banSeconds, TimeUnit.SECONDS);
        }
    }

    private long calculateBanTime(Long violations) {

        if (violations >= 10) return 30 * 60; // 30 min
        if (violations >= 5)  return 5 * 60;  // 5 min
        if (violations >= 3)  return 60;       // 1 min

        return 0;
    }
}
