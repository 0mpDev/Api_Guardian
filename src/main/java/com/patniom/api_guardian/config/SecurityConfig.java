package com.patniom.api_guardian.config;

import com.patniom.api_guardian.ratelimit.RateLimitingFilter;
import com.patniom.api_guardian.security.apikey.ApiKeyFilter;
import com.patniom.api_guardian.security.jwt.JwtFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

    @Autowired
    private JwtFilter jwtFilter;

    @Autowired
    private ApiKeyFilter apiKeyFilter;

    @Autowired
    private RateLimitingFilter rateLimitingFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        // Public endpoints (no auth required)
                        .requestMatchers("/api/test/**").permitAll()
                        .requestMatchers("/api/auth/**").permitAll() // Login endpoint
                        .requestMatchers("/error").permitAll()
                        .requestMatchers("/actuator/**").permitAll() // For monitoring (Phase 5)

                        // 🔒 CRITICAL: API Key management endpoints - MUST be authenticated
                        // Anyone with access can generate unlimited premium keys!
                        .requestMatchers("/api/keys/**").authenticated()

                        // 🔒 CRITICAL: Admin endpoints - MUST be authenticated
                        // Anyone with access can clear bans and reset rate limits!
                        .requestMatchers("/api/admin/**").authenticated()
                        // TODO: Add role-based access control: .requestMatchers("/api/admin/**").hasRole("ADMIN")

                        // All other endpoints require authentication
                        .anyRequest().authenticated()
                )

                // FILTER EXECUTION ORDER (CRITICAL):
                // 1. JWT Filter - Extracts user from token
                // 2. API Key Filter - Validates API key and checks permissions
                // 3. Rate Limiting Filter - Enforces rate limits based on tier

                .addFilterBefore(jwtFilter,
                        UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(apiKeyFilter,
                        JwtFilter.class)
                .addFilterAfter(rateLimitingFilter,
                        ApiKeyFilter.class);

        return http.build();
    }
}