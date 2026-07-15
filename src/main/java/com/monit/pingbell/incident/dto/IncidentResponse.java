package com.monit.pingbell.incident.dto;

import com.monit.pingbell.incident.domain.Incident;
import com.monit.pingbell.incident.domain.IncidentStatus;

import java.time.LocalDateTime;

public record IncidentResponse(
        Long id,
        Long monitorId,
        String monitorName,
        IncidentStatus status,
        LocalDateTime startedAt,
        LocalDateTime resolvedAt,
        String lastErrorMessage,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static IncidentResponse from(Incident incident) {
        return new IncidentResponse(
                incident.getId(),
                incident.getMonitor().getId(),
                incident.getMonitor().getName(),
                incident.getStatus(),
                incident.getStartedAt(),
                incident.getResolvedAt(),
                incident.getLastErrorMessage(),
                incident.getCreatedAt(),
                incident.getUpdatedAt()
        );
    }
}
