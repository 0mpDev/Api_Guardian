package com.patniom.api_guardian.audit;

import com.patniom.api_guardian.audit.projection.*;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AuditAnalyticsService {

    private final AuditLogRepository repository;

    public AuditAnalyticsService(AuditLogRepository repository) {
        this.repository = repository;
    }

    public List<BlockedIdentifierView> topBlocked() {
        return repository.findTopBlockedIdentifiers();
    }

    public List<EndpointStatsView> requestsPerEndpoint() {
        return repository.countRequestsPerEndpoint();
    }

    public List<DecisionStatsView> decisionStats() {
        return repository.countByDecision();
    }

    public List<HourlyTrafficView> hourlyTraffic() {
        return repository.hourlyTraffic();
    }
}
