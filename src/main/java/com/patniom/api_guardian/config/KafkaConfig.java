package com.patniom.api_guardian.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {

    @Value("${kafka.topics.api-requests}")
    private String apiRequestsTopic;

    @Value("${kafka.topics.rate-limit-violations}")
    private String rateLimitViolationsTopic;

    @Value("${kafka.topics.api-key-usage}")
    private String apiKeyUsageTopic;

    @Value("${kafka.topics.ban-events}")
    private String banEventsTopic;

    /**
     * Topic for all API requests
     * High throughput - stores every request
     */
    @Bean
    public NewTopic apiRequestsTopic() {
        return TopicBuilder.name(apiRequestsTopic)
                .partitions(3)  // For parallel processing
                .replicas(1)    // Single replica for dev (increase in prod)
                .compact()      // Keep only latest per key (optional)
                .build();
    }

    /**
     * Topic for rate limit violations
     * Medium throughput - only violations
     */
    @Bean
    public NewTopic rateLimitViolationsTopic() {
        return TopicBuilder.name(rateLimitViolationsTopic)
                .partitions(2)
                .replicas(1)
                .build();
    }

    /**
     * Topic for API key usage statistics
     * Used to batch-update MongoDB without blocking requests
     */
    @Bean
    public NewTopic apiKeyUsageTopic() {
        return TopicBuilder.name(apiKeyUsageTopic)
                .partitions(2)
                .replicas(1)
                .build();
    }

    /**
     * Topic for ban events
     * Low throughput - only when users get banned
     */
    @Bean
    public NewTopic banEventsTopic() {
        return TopicBuilder.name(banEventsTopic)
                .partitions(1)
                .replicas(1)
                .build();
    }
}