package com.patniom.api_guardian.audit;

import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;

@Aspect
@Component
@Profile("disabled")
@Deprecated
public class AuditLoggingAspect {

//    @Autowired
//    private AuditLogRepository auditLogRepository;
//
//    @AfterReturning(
//            pointcut = "within(@org.springframework.web.bind.annotation.RestController *)"
//    )
//    public void logAudit(JoinPoint joinPoint) {
//
//        ServletRequestAttributes attrs =
//                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
//
//        if (attrs == null) return;
//
//        HttpServletRequest request = attrs.getRequest();
//
//        String decision = (String) request.getAttribute("AUDIT_DECISION");
//        if (decision == null) return;
//
//        AuditLog log = new AuditLog();
//        log.setIdentifier(request.getRemoteAddr());
//        log.setEndpoint(request.getRequestURI());
//        log.setHttpMethod(request.getMethod());
//        log.setDecision(decision);
//        log.setTimestamp(LocalDateTime.now());
//
//        auditLogRepository.save(log);
//    }
}
