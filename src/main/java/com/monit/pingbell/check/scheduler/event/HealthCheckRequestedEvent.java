package com.monit.pingbell.check.scheduler.event;

import com.monit.pingbell.monitor.domain.Monitor;

import java.time.LocalDateTime;
import java.util.UUID;

public record HealthCheckRequestedEvent(
        UUID eventId,
        LocalDateTime occurredAt,
        Long monitorId,
        Long memberId,
        Integer timeoutMillis,
        Integer intervalSeconds,
        LocalDateTime scheduledAt,
        String requestedBy
) {
    private static final String SCHEDULER = "SCHEDULER";

    public static HealthCheckRequestedEvent from(Monitor monitor, LocalDateTime occurredAt) {
        return new HealthCheckRequestedEvent(
                UUID.randomUUID(),
                occurredAt,
                monitor.getId(),
                monitor.getMember().getId(),
                monitor.getTimeoutMillis(),
                monitor.getIntervalSeconds(),
                monitor.getNextCheckAt(),
                SCHEDULER
        );
    }
}
