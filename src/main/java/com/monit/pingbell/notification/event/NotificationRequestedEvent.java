package com.monit.pingbell.notification.event;

import com.monit.pingbell.incident.domain.Incident;
import com.monit.pingbell.notification.type.NotificationType;

import java.time.LocalDateTime;
import java.util.UUID;

public record NotificationRequestedEvent(
        UUID eventId,
        Long incidentId,
        Long monitorId,
        Long memberId,
        NotificationType notificationType,
        LocalDateTime occurredAt
) {
    public static NotificationRequestedEvent from(
            Incident incident,
            NotificationType notificationType,
            LocalDateTime occurredAt
    ) {
        return new NotificationRequestedEvent(
                UUID.randomUUID(),
                incident.getId(),
                incident.getMonitor().getId(),
                incident.getMonitor().getMember().getId(),
                notificationType,
                occurredAt
        );
    }
}
