package com.patniom.api_guardian.audit;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;


    public void log(String decision,
                    HttpServletRequest request,
                    String identifier) {

        AuditLog log = new AuditLog();
        log.setDecision(decision);
        log.setEndpoint(request.getRequestURI());
        log.setHttpMethod(request.getMethod());
        log.setIdentifier(identifier);
        log.setTimestamp(LocalDateTime.now());

        auditLogRepository.save(log);
    }
}
