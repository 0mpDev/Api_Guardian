package com.patniom.api_guardian.audit;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class AsyncAuditLogService {

    private final AuditLogRepository auditLogRepository;

    @Async
    public void log(String decision,
                    HttpServletRequest request,
                    String identifier) {
        try {
            AuditLog logEntity = new AuditLog();
            logEntity.setDecision(decision);
            logEntity.setEndpoint(request.getRequestURI());
            logEntity.setHttpMethod(request.getMethod());
            logEntity.setIdentifier(identifier);
            logEntity.setTimestamp(LocalDateTime.now());

            auditLogRepository.save(logEntity);

            // ✅ FIX: Use single String argument
            log.debug("✅ Audit log saved asynchronously: {} -> {}"
                    .formatted(identifier, decision));

        } catch (Exception e) {
            log.error("❌ Failed to save audit log asynchronously: " + e.getMessage(), e);
        }
    }
}
