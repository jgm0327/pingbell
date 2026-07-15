package com.monit.pingbell.notification.service;

import com.monit.pingbell.incident.domain.Incident;
import com.monit.pingbell.incident.repository.IncidentRepository;
import com.monit.pingbell.notification.event.NotificationRequestedEvent;
import com.monit.pingbell.notification.type.NotificationType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class NotificationWorkerService {
    private final IncidentRepository incidentRepository;
    private final NotificationService notificationService;

    public void handle(NotificationRequestedEvent event) {
        Incident incident = incidentRepository.findById(event.incidentId())
                .orElseThrow(() -> new IllegalArgumentException("Incident not found. incidentId=" + event.incidentId()));

        if (event.notificationType() == NotificationType.INCIDENT_OPEN) {
            notificationService.notifyIncidentOpened(incident, event.occurredAt());
            return;
        }

        notificationService.notifyIncidentResolved(incident, event.occurredAt());
    }
}
