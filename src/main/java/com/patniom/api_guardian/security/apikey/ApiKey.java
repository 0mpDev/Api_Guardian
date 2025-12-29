package com.patniom.api_guardian.security.apikey;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "api_keys")
public class ApiKey {

    @Id
    private String id;

    @Indexed(unique = true)
    private String keyValue; // The actual API key (hashed in production)

    @Indexed
    private String userId; // Owner of this API key

    private String name; // Friendly name: "Production API", "Dev Key", etc.

    private String description;

    @Indexed
    private ApiKeyStatus status; // ACTIVE, SUSPENDED, REVOKED, EXPIRED

    @Indexed
    private RateLimitTier tier; // FREE, BASIC, PREMIUM, ENTERPRISE

    // Quota Management
    private Long requestsPerMinute;
    private Long requestsPerHour;
    private Long requestsPerDay;
    private Long requestsPerMonth;

    // Usage Tracking (stored in MongoDB for analytics)
    private Long totalRequests;
    private Long successfulRequests;
    private Long failedRequests;
    private LocalDateTime lastUsedAt;

    // Quota Reset Tracking
    private LocalDateTime minuteResetAt;
    private LocalDateTime hourResetAt;
    private LocalDateTime dayResetAt;
    private LocalDateTime monthResetAt;

    // Security
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
    private LocalDateTime revokedAt;

    // IP Whitelisting (optional)
    private boolean ipWhitelistEnabled;
    private java.util.List<String> allowedIpAddresses;

    // Allowed Endpoints (optional - fine-grained access control)
    private java.util.List<String> allowedEndpoints; // ["/api/users/*", "/api/orders"]

    // Custom Metadata
    @Builder.Default
    private Map<String, String> metadata = new HashMap<>();

    // Soft delete
    private boolean deleted;

    public enum ApiKeyStatus {
        ACTIVE,
        SUSPENDED,
        REVOKED,
        EXPIRED
    }

    public enum RateLimitTier {
        FREE(10L, 100L, 1000L, 30000L),
        BASIC(50L, 1000L, 10000L, 300000L),
        PREMIUM(200L, 5000L, 50000L, 1500000L),
        ENTERPRISE(1000L, 50000L, 1000000L, 30000000L);

        private final Long requestsPerMinute;
        private final Long requestsPerHour;
        private final Long requestsPerDay;
        private final Long requestsPerMonth;

        RateLimitTier(Long rpm, Long rph, Long rpd, Long rpmth) {
            this.requestsPerMinute = rpm;
            this.requestsPerHour = rph;
            this.requestsPerDay = rpd;
            this.requestsPerMonth = rpmth;
        }

        public Long getRequestsPerMinute() { return requestsPerMinute; }
        public Long getRequestsPerHour() { return requestsPerHour; }
        public Long getRequestsPerDay() { return requestsPerDay; }
        public Long getRequestsPerMonth() { return requestsPerMonth; }
    }

    // Helper methods
    public boolean isActive() {
        return status == ApiKeyStatus.ACTIVE &&
                !deleted &&
                (expiresAt == null || expiresAt.isAfter(LocalDateTime.now()));
    }

    public boolean isExpired() {
        return expiresAt != null && expiresAt.isBefore(LocalDateTime.now());
    }

    public boolean isIpAllowed(String ipAddress) {
        if (!ipWhitelistEnabled || allowedIpAddresses == null || allowedIpAddresses.isEmpty()) {
            return true;
        }
        return allowedIpAddresses.contains(ipAddress);
    }

    public boolean isEndpointAllowed(String endpoint) {
        if (allowedEndpoints == null || allowedEndpoints.isEmpty()) {
            return true;
        }
        // Simple pattern matching (can be enhanced with regex)
        return allowedEndpoints.stream()
                .anyMatch(pattern -> endpoint.startsWith(pattern.replace("*", "")));
    }
}