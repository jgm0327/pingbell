package com.monit.pingbell.notification.service;

import com.monit.pingbell.incident.domain.Incident;
import com.monit.pingbell.notification.type.NotificationType;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "pingbell.check.dispatch-mode", havingValue = "direct", matchIfMissing = true)
public class DirectNotificationDispatchService implements NotificationDispatchService {
    private final NotificationService notificationService;

    @Override
    public void dispatch(Incident incident, NotificationType type, LocalDateTime occurredAt) {
        if (type == NotificationType.INCIDENT_OPEN) {
            notificationService.notifyIncidentOpened(incident, occurredAt);
            return;
        }

        notificationService.notifyIncidentResolved(incident, occurredAt);
    }
}
