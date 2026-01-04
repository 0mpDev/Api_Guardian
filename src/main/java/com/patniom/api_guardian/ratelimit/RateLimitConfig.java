package com.patniom.api_guardian.ratelimit;

import com.patniom.api_guardian.security.apikey.ApiKey;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RateLimitConfig {

    private ApiKey.RateLimitTier tier;
    private int capacity;           // Max tokens in bucket
    private long refillIntervalMs;  // Time to refill bucket

    // For sliding window strategy
    private Long requestsPerMinute;
    private Long requestsPerHour;
    private Long requestsPerDay;
}