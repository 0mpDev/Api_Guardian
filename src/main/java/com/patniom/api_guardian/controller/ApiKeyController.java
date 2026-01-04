package com.patniom.api_guardian.controller;

import com.patniom.api_guardian.security.apikey.ApiKey;
import com.patniom.api_guardian.security.apikey.ApiKeyService;
import com.patniom.api_guardian.security.apikey.GeneratedApiKeyResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/keys")
public class ApiKeyController {

    @Autowired
    private ApiKeyService apiKeyService;

    /**
     * Generate a new API key
     * POST /api/keys/generate
     *
     * SECURITY: userId is extracted from JWT token, NOT from request body
     */
    @PostMapping("/generate")
    public ResponseEntity<?> generateApiKey(
            @Valid @RequestBody GenerateKeyRequest request,
            HttpServletRequest httpRequest) {

        try {
            // 🔒 CRITICAL SECURITY: Always get userId from authenticated context
            // NEVER trust userId from request body - users could generate keys for others!
            String userId = (String) httpRequest.getAttribute("USER_ID");

            if (userId == null || userId.isEmpty()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of(
                                "error", "Unauthorized",
                                "message", "User authentication required. Please provide a valid JWT token."
                        ));
            }
            GeneratedApiKeyResponse response =
                    apiKeyService.generateApiKey(
                            userId,
                            request.getName(),
                            request.getTier(),
                            request.getExpiryDays()
                    );

            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "success", true,
                    "message", "API key generated successfully. Save it now!",
                    "apiKey", response.getApiKey(),
                    "keyId", response.getKeyId(),
                    "tier", response.getTier(),
                    "expiresAt", response.getExpiresAt()
            ));


        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * List all API keys for a user
     * GET /api/keys/user/{userId}
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<?> getUserApiKeys(@PathVariable String userId) {
        List<ApiKey> keys = apiKeyService.getUserApiKeys(userId);

        // Don't expose actual key values
        keys.forEach(key -> key.setKeyValue("***HIDDEN***"));

        return ResponseEntity.ok(Map.of(
                "success", true,
                "count", keys.size(),
                "keys", keys
        ));
    }

    /**
     * Revoke an API key
     * DELETE /api/keys/{keyId}
     */
    @DeleteMapping("/{keyId}")
    public ResponseEntity<?> revokeApiKey(@PathVariable String keyId) {
        try {
            apiKeyService.revokeApiKey(keyId);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "API key revoked successfully"
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Suspend an API key
     * POST /api/keys/{keyId}/suspend
     */
    @PostMapping("/{keyId}/suspend")
    public ResponseEntity<?> suspendApiKey(@PathVariable String keyId) {
        try {
            apiKeyService.suspendApiKey(keyId);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "API key suspended successfully"
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Reactivate a suspended API key
     * POST /api/keys/{keyId}/reactivate
     */
    @PostMapping("/{keyId}/reactivate")
    public ResponseEntity<?> reactivateApiKey(@PathVariable String keyId) {
        try {
            apiKeyService.reactivateApiKey(keyId);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "API key reactivated successfully"
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Upgrade API key tier
     * POST /api/keys/{keyId}/upgrade
     */
    @PostMapping("/{keyId}/upgrade")
    public ResponseEntity<?> upgradeApiKeyTier(
            @PathVariable String keyId,
            @RequestBody UpgradeTierRequest request) {
        try {
            apiKeyService.upgradeTier(keyId, request.getNewTier());
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "API key tier upgraded successfully",
                    "newTier", request.getNewTier()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Get API key statistics
     * GET /api/keys/{keyId}/stats
     */
    @GetMapping("/{keyId}/stats")
    public ResponseEntity<?> getApiKeyStats(@PathVariable String keyId) {
        // Implementation would fetch from MongoDB and return usage stats
        return ResponseEntity.ok(Map.of(
                "message", "Stats endpoint - to be implemented with analytics"
        ));
    }

    // ========== Request DTOs ==========

    @Data
    public static class GenerateKeyRequest {
        // ❌ REMOVED: userId (security risk - users could generate keys for others)
        // ✅ userId is now extracted from JWT token in the controller

        private String name;
        private ApiKey.RateLimitTier tier = ApiKey.RateLimitTier.FREE;
        private Integer expiryDays; // null = no expiry
    }

    @Data
    public static class UpgradeTierRequest {
        private ApiKey.RateLimitTier newTier;
    }
}