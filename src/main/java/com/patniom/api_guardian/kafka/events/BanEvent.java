package com.patniom.api_guardian.kafka.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BanEvent {
    private String identifier;       // Who got banned
    private String reason;           // Why they got banned
    private Integer violationCount;  // Number of violations
    private Long banDurationSeconds; // How long the ban lasts
    private LocalDateTime bannedAt;
    private LocalDateTime expiresAt;
}