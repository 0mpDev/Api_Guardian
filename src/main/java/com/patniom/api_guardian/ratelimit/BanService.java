package com.patniom.api_guardian.ratelimit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class BanService {

    private static final String PREFIX = "banned:";
    private static final Duration BAN_TIME = Duration.ofMinutes(5);

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    public void ban(String identifier) {
        redisTemplate.opsForValue().set(PREFIX + identifier, "1", BAN_TIME);
    }

    public boolean isBanned(String identifier) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(PREFIX + identifier));
    }
}
