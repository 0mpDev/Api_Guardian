package com.patniom.api_guardian.audit;

import com.patniom.api_guardian.audit.projection.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    // 1️⃣ Top blocked identifiers
    @Query("""
        SELECT a.identifier AS identifier, COUNT(a) AS count
        FROM AuditLog a
        WHERE a.decision IN ('BLOCK', 'BAN')
        GROUP BY a.identifier
        ORDER BY COUNT(a) DESC
    """)
    List<BlockedIdentifierView> findTopBlockedIdentifiers();

    // 2️⃣ Requests per endpoint
    @Query("""
        SELECT a.endpoint AS endpoint, COUNT(a) AS count
        FROM AuditLog a
        GROUP BY a.endpoint
    """)
    List<EndpointStatsView> countRequestsPerEndpoint();

    // 3️⃣ Decision breakdown
    @Query("""
        SELECT a.decision AS decision, COUNT(a) AS count
        FROM AuditLog a
        GROUP BY a.decision
    """)
    List<DecisionStatsView> countByDecision();

    // 4️⃣ Hourly traffic
    @Query("""
        SELECT HOUR(a.timestamp) AS hour, COUNT(a) AS count
        FROM AuditLog a
        GROUP BY HOUR(a.timestamp)
        ORDER BY hour
    """)
    List<HourlyTrafficView> hourlyTraffic();
}
