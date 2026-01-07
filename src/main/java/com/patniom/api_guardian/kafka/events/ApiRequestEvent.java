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
public class ApiRequestEvent {
    private String requestId;        // Unique request ID
    private String identifier;       // USER:xxx, KEY:xxx, or IP:xxx
    private String endpoint;         // /api/test/hello
    private String httpMethod;       // GET, POST, etc.
    private String decision;         // ALLOW, RATE_LIMIT, BANNED
    private String tier;             // FREE, BASIC, PREMIUM, ENTERPRISE
    private Integer statusCode;      // 200, 429, 403
    private Long responseTimeMs;     // How long the request took
    private String userAgent;        // Browser/client info
    private String ipAddress;        // Client IP
    private LocalDateTime timestamp;
}