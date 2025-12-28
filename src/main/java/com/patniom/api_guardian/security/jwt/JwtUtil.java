package com.patniom.api_guardian.security.jwt;

import org.springframework.stereotype.Component;

@Component
public class JwtUtil {
    public String extractUserId(String token) {
        // TEMP: fake logic for learning
        return "123";
    }
}
