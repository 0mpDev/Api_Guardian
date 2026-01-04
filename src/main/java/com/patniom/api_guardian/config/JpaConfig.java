package com.patniom.api_guardian.config;

import com.patniom.api_guardian.audit.AuditLogRepository;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@EnableJpaRepositories(
        basePackages = "com.patniom.api_guardian.audit",
        includeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = AuditLogRepository.class
        )
)
@EntityScan(basePackages = "com.patniom.api_guardian.audit")
public class JpaConfig {
}
