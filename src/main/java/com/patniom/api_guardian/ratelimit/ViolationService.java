package com.patniom.api_guardian.ratelimit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class ViolationService {

    private static final int BAN_THRESHOLD = 5;
    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final String PREFIX = "violations:";

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    public int recordViolation(String identifier) {
        String key = PREFIX + identifier;

        Long count = redisTemplate.opsForValue().increment(key);

        if (count == 1) {
            redisTemplate.expire(key, WINDOW);
        }

        return count.intValue();
    }

    public void reset(String identifier) {
        redisTemplate.delete(PREFIX + identifier);
    }
}
