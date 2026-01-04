package com.patniom.api_guardian.security.apikey;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class GeneratedApiKeyResponse {
    private String keyId;
    private String apiKey;      // RAW key (shown once)
    private ApiKey.RateLimitTier tier;
    private LocalDateTime expiresAt;
}
