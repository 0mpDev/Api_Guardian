package com.patniom.api_guardian.audit;

import com.patniom.api_guardian.audit.projection.*;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/analytics")
public class AuditAnalyticsController {

    private final AuditAnalyticsService service;

    public AuditAnalyticsController(AuditAnalyticsService service) {
        this.service = service;
    }

    @GetMapping("/top-blocked")
    public List<BlockedIdentifierView> topBlocked() {
        return service.topBlocked();
    }

    @GetMapping("/endpoints")
    public List<EndpointStatsView> endpoints() {
        return service.requestsPerEndpoint();
    }

    @GetMapping("/decisions")
    public List<DecisionStatsView> decisions() {
        return service.decisionStats();
    }

    @GetMapping("/hourly")
    public List<HourlyTrafficView> hourly() {
        return service.hourlyTraffic();
    }
}
