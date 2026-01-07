package com.patniom.api_guardian.security.jwt;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

@Slf4j
@Component
public class JwtFilter extends OncePerRequestFilter {

    @Autowired
    private JwtUtil jwtUtil;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);

            // Validate token
            if (jwtUtil.validateToken(token)) {
                String userId = jwtUtil.extractUserId(token);

                if (userId != null &&
                        SecurityContextHolder.getContext().getAuthentication() == null) {

                    // ✅ CRITICAL: Only set authentication if NOT already authenticated
                    // This prevents overwriting existing auth (OAuth2, SAML, etc.)
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(
                                    userId,              // Principal (username/userId)
                                    null,               // Credentials (not needed after authentication)
                                    Collections.emptyList()  // Authorities (roles/permissions - can add later)
                            );

                    authentication.setDetails(
                            new WebAuthenticationDetailsSource().buildDetails(request)
                    );

                    SecurityContextHolder.getContext().setAuthentication(authentication);

                    // Optional: Keep for backward compatibility with existing code
                    request.setAttribute("USER_ID", userId);

                    log.debug("✅ JWT authenticated user: {}", userId);
                } else if (userId == null) {
                    log.warn("⚠️ Valid JWT but no userId claim found");
                }
            } else {
                log.warn("⚠️ Invalid or expired JWT token");
            }
        }

        filterChain.doFilter(request, response);
    }
}