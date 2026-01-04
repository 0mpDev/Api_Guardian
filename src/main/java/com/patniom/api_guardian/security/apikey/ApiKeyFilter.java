package com.patniom.api_guardian.security.apikey;

import com.patniom.api_guardian.util.ClientIpUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

@Slf4j
@Component
public class ApiKeyFilter extends OncePerRequestFilter {

    @Autowired
    private ApiKeyService apiKeyService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String apiKeyHeader = request.getHeader("X-API-KEY");

        // If no API key provided, continue (might be JWT auth)
        if (apiKeyHeader == null || apiKeyHeader.trim().isEmpty()) {
            filterChain.doFilter(request, response);
            return;
        }

        // Validate API key
        Optional<ApiKey> apiKeyOpt = apiKeyService.validateApiKey(apiKeyHeader);

        if (apiKeyOpt.isEmpty()) {
            log.warn("Invalid API key attempted from IP: {}",
                    ClientIpUtil.getClientIp(request));

            request.setAttribute("AUDIT_DECISION", "INVALID_API_KEY");
            response.setStatus(401);
            response.setContentType("application/json");
            response.getWriter().write("""
                {
                    "error": "Unauthorized",
                    "message": "Invalid or expired API key"
                }
                """);
            return;
        }

        ApiKey apiKey = apiKeyOpt.get();

        // IP Whitelist Check
        String clientIp = ClientIpUtil.getClientIp(request);
        if (!apiKey.isIpAllowed(clientIp)) {
            log.warn("IP not whitelisted. API Key: {}, IP: {}",
                    apiKey.getId(), clientIp);

            request.setAttribute("AUDIT_DECISION", "IP_NOT_WHITELISTED");
            response.setStatus(403);
            response.setContentType("application/json");
            response.getWriter().write("""
                {
                    "error": "Forbidden",
                    "message": "Your IP address is not authorized for this API key"
                }
                """);
            return;
        }

        // Endpoint Access Control Check
        String requestURI = request.getRequestURI();
        if (!apiKey.isEndpointAllowed(requestURI)) {
            log.warn("Endpoint not allowed. API Key: {}, Endpoint: {}",
                    apiKey.getId(), requestURI);

            request.setAttribute("AUDIT_DECISION", "ENDPOINT_NOT_ALLOWED");
            response.setStatus(403);
            response.setContentType("application/json");
            response.getWriter().write("""
                {
                    "error": "Forbidden",
                    "message": "This API key does not have access to this endpoint"
                }
                """);
            return;
        }

        // Store API key details in request attributes for downstream filters
        request.setAttribute("API_KEY", apiKey.getKeyValue());
        request.setAttribute("API_KEY_ID", apiKey.getId());
        request.setAttribute("API_KEY_USER_ID", apiKey.getUserId());
        request.setAttribute("API_KEY_TIER", apiKey.getTier());
        request.setAttribute("API_KEY_OBJ", apiKey);

        // Add custom headers for backend services
        request.setAttribute("X-User-Id", apiKey.getUserId());
        request.setAttribute("X-Rate-Limit-Tier", apiKey.getTier().name());

        log.debug("API key validated successfully. User: {}, Tier: {}",
                apiKey.getUserId(), apiKey.getTier());

        filterChain.doFilter(request, response);
    }
}