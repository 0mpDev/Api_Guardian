package com.patniom.api_guardian.util;

import jakarta.servlet.http.HttpServletRequest;

public class ClientIpUtil {
    public static String getClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null) {
            return forwarded.split(",")[0];
        }
        return request.getRemoteAddr();
    }
}
