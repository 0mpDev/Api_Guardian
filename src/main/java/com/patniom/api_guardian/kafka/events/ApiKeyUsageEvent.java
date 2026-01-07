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
public class ApiKeyUsageEvent {
    private String apiKeyId;         // MongoDB ID
    private String userId;           // Owner of the key
    private String endpoint;         // Which endpoint was called
    private boolean success;         // Was the request successful?
    private String tier;             // Rate limit tier
    private LocalDateTime timestamp;
}