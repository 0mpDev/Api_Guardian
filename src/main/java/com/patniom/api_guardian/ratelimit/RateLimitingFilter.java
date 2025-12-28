package com.patniom.api_guardian.ratelimit;

import com.patniom.api_guardian.util.ClientIpUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class RateLimitingFilter extends OncePerRequestFilter {

    @Autowired
    private RateLimiterService rateLimiterService;

    @Autowired
    private AbuseDetectionService abuseDetectionService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String identifier = resolveIdentifier(request);

        // 🚫 STEP 1: Check ban
        if (abuseDetectionService.isBanned(identifier)) {

            // ✅ AUDIT DECISION
            request.setAttribute("AUDIT_DECISION", "BANNED");

            response.setStatus(403);
            response.getWriter().write("You are temporarily banned");
            return;
        }

        // ⏱ STEP 2: Rate limit
        boolean allowed = rateLimiterService.allowRequest(identifier);

        if (!allowed) {

            abuseDetectionService.recordViolation(identifier);

            // ✅ AUDIT DECISION
            request.setAttribute("AUDIT_DECISION", "RATE_LIMIT");

            response.setStatus(429);
            response.getWriter().write("Too many requests");
            return;
        }

        // ✅ AUDIT DECISION (SUCCESS CASE)
        request.setAttribute("AUDIT_DECISION", "ALLOW");

        filterChain.doFilter(request, response);
    }

    private String resolveIdentifier(HttpServletRequest request) {

        if (request.getAttribute("USER_ID") != null) {
            return "USER:" + request.getAttribute("USER_ID");
        }

        if (request.getAttribute("API_KEY") != null) {
            return "KEY:" + request.getAttribute("API_KEY");
        }

        return "IP:" + request.getRemoteAddr();
    }
}
