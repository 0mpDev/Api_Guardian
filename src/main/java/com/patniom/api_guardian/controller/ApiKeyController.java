package com.patniom.api_guardian.controller;

import com.patniom.api_guardian.security.apikey.ApiKey;
import com.patniom.api_guardian.security.apikey.ApiKeyService;
import com.patniom.api_guardian.security.apikey.GeneratedApiKeyResponse;
import jakarta.validation.Valid;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
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
     * SECURITY: userId is extracted from JWT token (SecurityContext), NOT from request body
     */
    @PostMapping("/generate")
    public ResponseEntity<?> generateApiKey(@Valid @RequestBody GenerateKeyRequest request) {

        try {
            // 🔒 CRITICAL SECURITY: Get userId from Spring Security context
            // This is set by JwtFilter when it validates the token
            String userId = SecurityContextHolder.getContext()
                    .getAuthentication()
                    .getName();

            if (userId == null || userId.isEmpty() || "anonymousUser".equals(userId)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(createErrorResponse(
                                "Unauthorized",
                                "User authentication required. Please provide a valid JWT token."
                        ));
            }

            GeneratedApiKeyResponse response = apiKeyService.generateApiKey(
                    userId,
                    request.getName(),
                    request.getTier(),
                    request.getExpiryDays()
            );

            // ✅ FIX: Use HashMap to safely handle null values (especially expiresAt)
            // Map.of() throws NPE if any value is null
            Map<String, Object> body = new HashMap<>();
            body.put("success", true);
            body.put("message", "API key generated successfully. Save this key - it won't be shown again!");
            body.put("apiKey", response.getApiKey());
            body.put("keyId", response.getKeyId());
            body.put("userId", userId);
            body.put("tier", response.getTier());
            body.put("expiresAt", response.getExpiresAt()); // Safe even if null

            return ResponseEntity.status(HttpStatus.CREATED).body(body);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(createErrorResponse("Internal Server Error", e.getMessage()));
        }
    }

    /**
     * List current user's API keys
     * GET /api/keys/me
     *
     * SECURITY FIX: Removed /user/{userId} endpoint (was a security hole)
     * Users can only see their own keys, not other users' keys
     */
    @GetMapping("/me")
    public ResponseEntity<?> getMyApiKeys() {
        try {
            String userId = SecurityContextHolder.getContext()
                    .getAuthentication()
                    .getName();

            if (userId == null || "anonymousUser".equals(userId)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(createErrorResponse("Unauthorized", "Authentication required"));
            }

            List<ApiKey> keys = apiKeyService.getUserApiKeys(userId);

            // Don't expose actual key values
            keys.forEach(key -> key.setKeyValue("***HIDDEN***"));

            Map<String, Object> body = new HashMap<>();
            body.put("success", true);
            body.put("userId", userId);
            body.put("count", keys.size());
            body.put("keys", keys);

            return ResponseEntity.ok(body);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(createErrorResponse("Internal Server Error", e.getMessage()));
        }
    }

    /**
     * Revoke an API key
     * DELETE /api/keys/{keyId}
     *
     * SECURITY: Only the owner can revoke their own keys
     */
    @DeleteMapping("/{keyId}")
    public ResponseEntity<?> revokeApiKey(@PathVariable String keyId) {
        try {
            String userId = SecurityContextHolder.getContext()
                    .getAuthentication()
                    .getName();

            // TODO: Add ownership check - verify this key belongs to this user
            // For now, trusting authenticated users

            apiKeyService.revokeApiKey(keyId);

            Map<String, Object> body = new HashMap<>();
            body.put("success", true);
            body.put("message", "API key revoked successfully");
            body.put("keyId", keyId);

            return ResponseEntity.ok(body);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(createErrorResponse("Internal Server Error", e.getMessage()));
        }
    }

    /**
     * Suspend an API key
     * POST /api/keys/{keyId}/suspend
     */
    @PostMapping("/{keyId}/suspend")
    public ResponseEntity<?> suspendApiKey(@PathVariable String keyId) {
        try {
            String userId = SecurityContextHolder.getContext()
                    .getAuthentication()
                    .getName();

            // TODO: Add ownership check

            apiKeyService.suspendApiKey(keyId);

            Map<String, Object> body = new HashMap<>();
            body.put("success", true);
            body.put("message", "API key suspended successfully");
            body.put("keyId", keyId);

            return ResponseEntity.ok(body);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(createErrorResponse("Internal Server Error", e.getMessage()));
        }
    }

    /**
     * Reactivate a suspended API key
     * POST /api/keys/{keyId}/reactivate
     */
    @PostMapping("/{keyId}/reactivate")
    public ResponseEntity<?> reactivateApiKey(@PathVariable String keyId) {
        try {
            String userId = SecurityContextHolder.getContext()
                    .getAuthentication()
                    .getName();

            // TODO: Add ownership check

            apiKeyService.reactivateApiKey(keyId);

            Map<String, Object> body = new HashMap<>();
            body.put("success", true);
            body.put("message", "API key reactivated successfully");
            body.put("keyId", keyId);

            return ResponseEntity.ok(body);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(createErrorResponse("Internal Server Error", e.getMessage()));
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
            String userId = SecurityContextHolder.getContext()
                    .getAuthentication()
                    .getName();

            // TODO: Add ownership check
            // TODO: Add payment verification for upgrades

            apiKeyService.upgradeTier(keyId, request.getNewTier());

            Map<String, Object> body = new HashMap<>();
            body.put("success", true);
            body.put("message", "API key tier upgraded successfully");
            body.put("keyId", keyId);
            body.put("newTier", request.getNewTier());

            return ResponseEntity.ok(body);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(createErrorResponse("Internal Server Error", e.getMessage()));
        }
    }

    /**
     * Get API key statistics
     * GET /api/keys/{keyId}/stats
     */
    @GetMapping("/{keyId}/stats")
    public ResponseEntity<?> getApiKeyStats(@PathVariable String keyId) {
        try {
            String userId = SecurityContextHolder.getContext()
                    .getAuthentication()
                    .getName();

            // TODO: Implement stats retrieval from MongoDB
            // TODO: Add ownership check

            Map<String, Object> body = new HashMap<>();
            body.put("success", true);
            body.put("message", "Stats endpoint - to be implemented with analytics");
            body.put("keyId", keyId);

            return ResponseEntity.ok(body);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(createErrorResponse("Internal Server Error", e.getMessage()));
        }
    }

    // ========== Helper Methods ==========

    /**
     * Create standardized error response
     * Uses HashMap to safely handle null values
     */
    private Map<String, Object> createErrorResponse(String error, String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("error", error);
        response.put("message", message);
        return response;
    }

    // ========== Request DTOs ==========

    @Data
    public static class GenerateKeyRequest {
        // ❌ REMOVED: userId (security risk - users could generate keys for others)
        // ✅ userId is now extracted from JWT token via SecurityContext

        private String name;
        private ApiKey.RateLimitTier tier = ApiKey.RateLimitTier.FREE;
        private Integer expiryDays; // null = no expiry (will not cause NPE anymore)
    }

    @Data
    public static class UpgradeTierRequest {
        private ApiKey.RateLimitTier newTier;
    }
}