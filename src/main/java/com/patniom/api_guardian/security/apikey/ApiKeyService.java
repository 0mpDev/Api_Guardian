package com.patniom.api_guardian.security.apikey;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class ApiKeyService {

    private static final String API_KEY_PREFIX = "agk_"; // API Guardian Key
    private static final int KEY_LENGTH = 32;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Autowired
    private ApiKeyRepository apiKeyRepository;

    /**
     * Generate a new API key for a user
     */
    public GeneratedApiKeyResponse generateApiKey(
            String userId,
            String name,
            ApiKey.RateLimitTier tier,
            Integer expiryDays) {

        String rawKey = generateSecureKey();
        String hashedKey = hashApiKey(rawKey);

        ApiKey finalKey = ApiKey.builder()
                .keyValue(hashedKey)   // ✅ ONLY hashed key stored
                .userId(userId)
                .name(name)
                .status(ApiKey.ApiKeyStatus.ACTIVE)
                .tier(tier != null ? tier : ApiKey.RateLimitTier.FREE)
                .createdAt(LocalDateTime.now())
                .expiresAt(expiryDays != null
                        ? LocalDateTime.now().plusDays(expiryDays)
                        : null)
                .deleted(false)
                .build();

        apiKeyRepository.save(finalKey); // ✅ no mutation after save

        // ✅ RAW key returned separately
        return new GeneratedApiKeyResponse(
                finalKey.getId(),
                rawKey,
                finalKey.getTier(),
                finalKey.getExpiresAt()
        );
    }


    /**
     * Validate API key and return details
     */
    public Optional<ApiKey> validateApiKey(String rawKey) {
        String hashedKey = hashApiKey(rawKey);
        Optional<ApiKey> apiKeyOpt = apiKeyRepository.findByKeyValue(hashedKey);

        if (apiKeyOpt.isEmpty()) {
            log.warn("API key not found: {}", rawKey.substring(0, 10) + "...");
            return Optional.empty();
        }

        ApiKey apiKey = apiKeyOpt.get();

        // Check if key is active
        if (!apiKey.isActive()) {
            log.warn("API key is not active: {}, status: {}",
                    apiKey.getId(), apiKey.getStatus());
            return Optional.empty();
        }

        // Check expiration
        if (apiKey.isExpired()) {
            log.warn("API key expired: {}, expired at: {}",
                    apiKey.getId(), apiKey.getExpiresAt());
            apiKey.setStatus(ApiKey.ApiKeyStatus.EXPIRED);
            apiKeyRepository.save(apiKey);
            return Optional.empty();
        }

        // Update last used timestamp
        apiKey.setLastUsedAt(LocalDateTime.now());
        apiKeyRepository.save(apiKey);

        return Optional.of(apiKey);
    }

    /**
     * Check if request is within quota limits
     */
    public boolean isWithinQuota(ApiKey apiKey, String timeWindow) {
        LocalDateTime now = LocalDateTime.now();

        switch (timeWindow.toLowerCase()) {
            case "minute":
                if (apiKey.getMinuteResetAt() == null ||
                        apiKey.getMinuteResetAt().isBefore(now)) {
                    apiKey.setMinuteResetAt(now.plusMinutes(1));
                    return true;
                }
                return apiKey.getTotalRequests() < apiKey.getRequestsPerMinute();

            case "hour":
                if (apiKey.getHourResetAt() == null ||
                        apiKey.getHourResetAt().isBefore(now)) {
                    apiKey.setHourResetAt(now.plusHours(1));
                    return true;
                }
                return apiKey.getTotalRequests() < apiKey.getRequestsPerHour();

            case "day":
                if (apiKey.getDayResetAt() == null ||
                        apiKey.getDayResetAt().isBefore(now)) {
                    apiKey.setDayResetAt(now.plusDays(1));
                    return true;
                }
                return apiKey.getTotalRequests() < apiKey.getRequestsPerDay();

            default:
                return true;
        }
    }

    /**
     * Increment usage counter
     */
    public void incrementUsage(ApiKey apiKey, boolean success) {
        apiKey.setTotalRequests(apiKey.getTotalRequests() + 1);

        if (success) {
            apiKey.setSuccessfulRequests(apiKey.getSuccessfulRequests() + 1);
        } else {
            apiKey.setFailedRequests(apiKey.getFailedRequests() + 1);
        }

        apiKeyRepository.save(apiKey);
    }

    /**
     * Revoke an API key
     */
    public void revokeApiKey(String apiKeyId) {
        Optional<ApiKey> apiKeyOpt = apiKeyRepository.findById(apiKeyId);

        if (apiKeyOpt.isPresent()) {
            ApiKey apiKey = apiKeyOpt.get();
            apiKey.setStatus(ApiKey.ApiKeyStatus.REVOKED);
            apiKey.setRevokedAt(LocalDateTime.now());
            apiKeyRepository.save(apiKey);

            log.info("Revoked API key: {}", apiKeyId);
        }
    }

    /**
     * Suspend an API key temporarily
     */
    public void suspendApiKey(String apiKeyId) {
        Optional<ApiKey> apiKeyOpt = apiKeyRepository.findById(apiKeyId);

        if (apiKeyOpt.isPresent()) {
            ApiKey apiKey = apiKeyOpt.get();
            apiKey.setStatus(ApiKey.ApiKeyStatus.SUSPENDED);
            apiKeyRepository.save(apiKey);

            log.info("Suspended API key: {}", apiKeyId);
        }
    }

    /**
     * Reactivate a suspended key
     */
    public void reactivateApiKey(String apiKeyId) {
        Optional<ApiKey> apiKeyOpt = apiKeyRepository.findById(apiKeyId);

        if (apiKeyOpt.isPresent()) {
            ApiKey apiKey = apiKeyOpt.get();
            if (apiKey.getStatus() == ApiKey.ApiKeyStatus.SUSPENDED) {
                apiKey.setStatus(ApiKey.ApiKeyStatus.ACTIVE);
                apiKeyRepository.save(apiKey);

                log.info("Reactivated API key: {}", apiKeyId);
            }
        }
    }

    /**
     * Get all keys for a user
     */
    public List<ApiKey> getUserApiKeys(String userId) {
        return apiKeyRepository.findActiveKeysByUserId(userId);
    }

    /**
     * Upgrade tier
     */
    public void upgradeTier(String apiKeyId, ApiKey.RateLimitTier newTier) {
        Optional<ApiKey> apiKeyOpt = apiKeyRepository.findById(apiKeyId);

        if (apiKeyOpt.isPresent()) {
            ApiKey apiKey = apiKeyOpt.get();
            apiKey.setTier(newTier);
            apiKey.setRequestsPerMinute(newTier.getRequestsPerMinute());
            apiKey.setRequestsPerHour(newTier.getRequestsPerHour());
            apiKey.setRequestsPerDay(newTier.getRequestsPerDay());
            apiKey.setRequestsPerMonth(newTier.getRequestsPerMonth());

            apiKeyRepository.save(apiKey);
            log.info("Upgraded API key {} to tier: {}", apiKeyId, newTier);
        }
    }

    /**
     * Clean up expired keys (run as scheduled job)
     */
    public int cleanupExpiredKeys() {
        List<ApiKey> expiredKeys = apiKeyRepository
                .findExpiredKeys(LocalDateTime.now());

        expiredKeys.forEach(key -> {
            key.setStatus(ApiKey.ApiKeyStatus.EXPIRED);
            apiKeyRepository.save(key);
        });

        log.info("Marked {} expired keys", expiredKeys.size());
        return expiredKeys.size();
    }

    // ========== Helper Methods ==========

    private String generateSecureKey() {
        byte[] randomBytes = new byte[KEY_LENGTH];
        SECURE_RANDOM.nextBytes(randomBytes);
        String encoded = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(randomBytes);

        return API_KEY_PREFIX + encoded;
    }

    private String hashApiKey(String rawKey) {
        // In production, use SHA-256 + salt
        return DigestUtils.sha256Hex(rawKey);
    }
}