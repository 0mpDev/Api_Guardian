package com.patniom.api_guardian.controller;

import com.patniom.api_guardian.security.jwt.JwtUtil;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private JwtUtil jwtUtil;

    /**
     * Simplified login for testing
     * In production, this would verify credentials against a database
     *
     * POST /api/auth/login
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {

        // TODO: In production, verify username/password against database
        // For now, we just generate a token for any username

        String token = jwtUtil.generateToken(request.getUsername());

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Login successful",
                "token", token,
                "userId", request.getUsername(),
                "expiresIn", "24 hours"
        ));
    }

    /**
     * Verify token endpoint (for testing)
     * GET /api/auth/verify
     */
    @GetMapping("/verify")
    public ResponseEntity<?> verify(@RequestHeader("Authorization") String authHeader) {

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(401)
                    .body(Map.of("error", "Missing or invalid Authorization header"));
        }

        String token = authHeader.substring(7);

        if (jwtUtil.validateToken(token)) {
            String userId = jwtUtil.extractUserId(token);
            return ResponseEntity.ok(Map.of(
                    "valid", true,
                    "userId", userId
            ));
        }

        return ResponseEntity.status(401)
                .body(Map.of("valid", false, "error", "Invalid or expired token"));
    }

    @Data
    public static class LoginRequest {
        private String username;
        // In production, you'd also have:
        // private String password;
    }
}