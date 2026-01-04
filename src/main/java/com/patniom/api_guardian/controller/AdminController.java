package com.patniom.api_guardian.controller;

import com.patniom.api_guardian.ratelimit.AbuseDetectionService;
import com.patniom.api_guardian.ratelimit.RateLimiterService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    @Autowired
    private AbuseDetectionService abuseDetectionService;

    @Autowired
    private RateLimiterService rateLimiterService;

    /**
     * Clear ban for specific identifier
     * POST /api/admin/ban/clear/{identifier}
     */
    @PostMapping("/ban/clear/{identifier}")
    public ResponseEntity<?> clearBan(@PathVariable String identifier) {
        abuseDetectionService.clearBan(identifier);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Ban cleared for: " + identifier
        ));
    }

    /**
     * Clear all bans (use carefully!)
     * POST /api/admin/ban/clear-all
     */
    @PostMapping("/ban/clear-all")
    public ResponseEntity<?> clearAllBans() {
        abuseDetectionService.clearAllBans();
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "All bans cleared"
        ));
    }

    /**
     * Get violation count for identifier
     * GET /api/admin/violations/{identifier}
     */
    @GetMapping("/violations/{identifier}")
    public ResponseEntity<?> getViolations(@PathVariable String identifier) {
        long violations = abuseDetectionService.getViolationCount(identifier);
        long remainingBan = abuseDetectionService.getRemainingBanTime(identifier);
        boolean isBanned = abuseDetectionService.isBanned(identifier);

        return ResponseEntity.ok(Map.of(
                "identifier", identifier,
                "violations", violations,
                "isBanned", isBanned,
                "remainingBanSeconds", remainingBan
        ));
    }

    /**
     * Reset rate limit for identifier
     * POST /api/admin/ratelimit/reset/{identifier}
     */
    @PostMapping("/ratelimit/reset/{identifier}")
    public ResponseEntity<?> resetRateLimit(@PathVariable String identifier) {
        rateLimiterService.resetRateLimit(identifier);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Rate limit reset for: " + identifier
        ));
    }
}