package com.patniom.api_guardian.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
public class MetricsService {

    private final MeterRegistry meterRegistry;

    // ===== STATIC METERS (SAFE) =====
    private final Counter requestsTotal;
    private final Counter requestsAllowed;
    private final Counter requestsRateLimited;
    private final Counter requestsBanned;
    private final Counter violationsTotal;
    private final Counter bansTotal;

    private final AtomicLong activeBans = new AtomicLong(0);
    private final AtomicLong activeApiKeys = new AtomicLong(0);

    private final Timer requestDuration;

    // ===== DYNAMIC METERS (SEPARATED CACHES) =====
    private final ConcurrentHashMap<String, Counter> endpointCounters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Counter> tierCounters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Counter> violationCounters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Counter> banCounters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Counter> kafkaCounters = new ConcurrentHashMap<>();

    public MetricsService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;

        // ---- Core counters ----
        this.requestsTotal = Counter.builder("api_guardian_requests_total")
                .description("Total API requests")
                .register(meterRegistry);

        this.requestsAllowed = Counter.builder("api_guardian_requests_allowed_total")
                .description("Allowed requests")
                .register(meterRegistry);

        this.requestsRateLimited = Counter.builder("api_guardian_requests_rate_limited_total")
                .description("Rate limited requests")
                .register(meterRegistry);

        this.requestsBanned = Counter.builder("api_guardian_requests_banned_total")
                .description("Banned requests")
                .register(meterRegistry);

        this.violationsTotal = Counter.builder("api_guardian_violations_total")
                .description("Total rate limit violations")
                .register(meterRegistry);

        this.bansTotal = Counter.builder("api_guardian_bans_total")
                .description("Total bans applied")
                .register(meterRegistry);

        // ---- Gauges ----
        Gauge.builder("api_guardian_active_bans", activeBans, AtomicLong::get)
                .description("Currently active bans")
                .register(meterRegistry);

        Gauge.builder("api_guardian_active_api_keys", activeApiKeys, AtomicLong::get)
                .description("Active API keys")
                .register(meterRegistry);

        // ---- Timer ----
        this.requestDuration = Timer.builder("api_guardian_request_duration")
                .description("Request processing latency")
                .publishPercentileHistogram()
                .register(meterRegistry);
    }

    // ================= REQUEST METRICS =================

    public void recordRequest(String decision, String tier, String endpoint) {
        requestsTotal.increment();

        switch (decision) {
            case "ALLOW" -> {
                requestsAllowed.increment();
                if (tier != null) recordByTier(tier, "allowed");
            }
            case "RATE_LIMIT" -> {
                requestsRateLimited.increment();
                if (tier != null) recordByTier(tier, "rate_limited");
            }
            case "BANNED" -> requestsBanned.increment();
        }

        recordByEndpoint(normalizeEndpoint(endpoint), decision);
    }

    private void recordByEndpoint(String endpoint, String decision) {
        String key = endpoint + ":" + decision;

        Counter counter = endpointCounters.computeIfAbsent(key, k ->
                Counter.builder("api_guardian_requests_by_endpoint")
                        .tags("endpoint", endpoint, "decision", decision)
                        .description("Requests by endpoint and decision")
                        .register(meterRegistry)
        );
        counter.increment();
    }

    private void recordByTier(String tier, String decision) {
        String key = tier + ":" + decision;

        Counter counter = tierCounters.computeIfAbsent(key, k ->
                Counter.builder("api_guardian_requests_by_tier")
                        .tags("tier", tier, "decision", decision)
                        .description("Requests by tier and decision")
                        .register(meterRegistry)
        );
        counter.increment();
    }

    private String normalizeEndpoint(String uri) {
        if (uri == null) return "/unknown";
        if (uri.startsWith("/api/keys")) return "/api/keys/*";
        if (uri.startsWith("/api/test")) return "/api/test/*";
        if (uri.startsWith("/api/auth")) return "/api/auth/*";
        if (uri.startsWith("/api/admin")) return "/api/admin/*";
        if (uri.startsWith("/actuator")) return "/actuator/*";
        return "/other";
    }

    // ================= TIMER =================

    public Timer.Sample startTimer() {
        return Timer.start(meterRegistry);
    }

    public void stopTimer(Timer.Sample sample) {
        sample.stop(requestDuration);
    }

    // ================= VIOLATION & BAN =================

    public void recordViolation(String identifier) {
        violationsTotal.increment();

        String type = extractIdentifierType(identifier);
        Counter counter = violationCounters.computeIfAbsent(type, t ->
                Counter.builder("api_guardian_violations_by_type")
                        .tag("type", t)
                        .description("Violations by identifier type")
                        .register(meterRegistry)
        );
        counter.increment();
    }

    public void recordBan(String identifier, long durationSeconds) {
        bansTotal.increment();
        activeBans.incrementAndGet();

        String type = extractIdentifierType(identifier);
        Counter counter = banCounters.computeIfAbsent(type, t ->
                Counter.builder("api_guardian_bans_by_type")
                        .tag("type", t)
                        .description("Bans by identifier type")
                        .register(meterRegistry)
        );
        counter.increment();

        meterRegistry.summary("api_guardian_ban_duration_seconds")
                .record(durationSeconds);
    }

    public void recordBanExpired() {
        activeBans.decrementAndGet();
    }

    private String extractIdentifierType(String identifier) {
        return identifier != null && identifier.contains(":")
                ? identifier.split(":")[0]
                : "UNKNOWN";
    }

    // ================= API KEY =================

    public void setActiveApiKeys(long count) {
        activeApiKeys.set(count);
    }

    public void recordApiKeyCreated(String tier) {
        Counter counter = tierCounters.computeIfAbsent("created:" + tier, k ->
                Counter.builder("api_guardian_api_keys_created")
                        .tag("tier", tier)
                        .description("API keys created")
                        .register(meterRegistry)
        );
        counter.increment();
    }

    public void recordApiKeyRevoked(String tier) {
        Counter counter = tierCounters.computeIfAbsent("revoked:" + tier, k ->
                Counter.builder("api_guardian_api_keys_revoked")
                        .tag("tier", tier)
                        .description("API keys revoked")
                        .register(meterRegistry)
        );
        counter.increment();
    }

    // ================= KAFKA =================

    public void recordKafkaProduced(String topic) {
        Counter counter = kafkaCounters.computeIfAbsent("produced:" + topic, k ->
                Counter.builder("api_guardian_kafka_messages_produced")
                        .tag("topic", topic)
                        .description("Kafka messages produced")
                        .register(meterRegistry)
        );
        counter.increment();
    }

    public void recordKafkaConsumed(String topic) {
        Counter counter = kafkaCounters.computeIfAbsent("consumed:" + topic, k ->
                Counter.builder("api_guardian_kafka_messages_consumed")
                        .tag("topic", topic)
                        .description("Kafka messages consumed")
                        .register(meterRegistry)
        );
        counter.increment();
    }

    public void recordKafkaError(String topic, String errorType) {
        String key = topic + ":" + errorType;

        Counter counter = kafkaCounters.computeIfAbsent(key, k ->
                Counter.builder("api_guardian_kafka_errors")
                        .tags("topic", topic, "error_type", errorType)
                        .description("Kafka errors")
                        .register(meterRegistry)
        );
        counter.increment();
    }
}
