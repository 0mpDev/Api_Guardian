package com.patniom.api_guardian.config;

import com.patniom.api_guardian.security.apikey.ApiKeyRepository;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

@Configuration
@EnableMongoRepositories(
        basePackages = "com.patniom.api_guardian.security.apikey",
        includeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = ApiKeyRepository.class
        )
)
public class MongoConfig {
}
