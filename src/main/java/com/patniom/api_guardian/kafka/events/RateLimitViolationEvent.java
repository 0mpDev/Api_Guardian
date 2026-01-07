package com.patniom.api_guardian.kafka.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RateLimitViolationEvent {
    private String identifier;       // Who violated
    private String endpoint;         // Which endpoint
    private String tier;             // Rate limit tier
    private Integer violationCount;  // How many violations so far
    private Long retryAfterSeconds;  // When they can try again
    private LocalDateTime timestamp;
}
