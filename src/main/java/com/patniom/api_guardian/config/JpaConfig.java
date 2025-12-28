package com.patniom.api_guardian.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.boot.autoconfigure.domain.EntityScan;

@Configuration
@EnableJpaRepositories(basePackages = "com.patniom.api_guardian.audit")
@EntityScan(basePackages = "com.patniom.api_guardian.audit")
public class JpaConfig {
}
