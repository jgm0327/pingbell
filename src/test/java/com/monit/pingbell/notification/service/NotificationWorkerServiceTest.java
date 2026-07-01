package com.monit.pingbell.notification.service;

import com.monit.pingbell.incident.domain.Incident;
import com.monit.pingbell.incident.domain.IncidentStatus;
import com.monit.pingbell.incident.repository.IncidentRepository;
import com.monit.pingbell.notification.event.NotificationRequestedEvent;
import com.monit.pingbell.notification.type.NotificationType;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationWorkerServiceTest {

    @Test
    void handleSendsIncidentOpenedNotification() {
        IncidentRepository incidentRepository = mock(IncidentRepository.class);
        NotificationService notificationService = mock(NotificationService.class);
        NotificationWorkerService workerService = new NotificationWorkerService(incidentRepository, notificationService);
        Incident incident = incident();
        LocalDateTime occurredAt = LocalDateTime.of(2026, 7, 1, 10, 0);
        NotificationRequestedEvent event = event(NotificationType.INCIDENT_OPEN, occurredAt);

        when(incidentRepository.findById(10L)).thenReturn(Optional.of(incident));

        workerService.handle(event);

        verify(notificationService).notifyIncidentOpened(incident, occurredAt);
    }

    @Test
    void handleSendsIncidentResolvedNotification() {
        IncidentRepository incidentRepository = mock(IncidentRepository.class);
        NotificationService notificationService = mock(NotificationService.class);
        NotificationWorkerService workerService = new NotificationWorkerService(incidentRepository, notificationService);
        Incident incident = incident();
        LocalDateTime occurredAt = LocalDateTime.of(2026, 7, 1, 10, 0);
        NotificationRequestedEvent event = event(NotificationType.INCIDENT_RESOLVED, occurredAt);

        when(incidentRepository.findById(10L)).thenReturn(Optional.of(incident));

        workerService.handle(event);

        verify(notificationService).notifyIncidentResolved(incident, occurredAt);
    }

    @Test
    void handleThrowsWhenIncidentDoesNotExist() {
        IncidentRepository incidentRepository = mock(IncidentRepository.class);
        NotificationService notificationService = mock(NotificationService.class);
        NotificationWorkerService workerService = new NotificationWorkerService(incidentRepository, notificationService);
        NotificationRequestedEvent event = event(NotificationType.INCIDENT_OPEN, LocalDateTime.of(2026, 7, 1, 10, 0));

        when(incidentRepository.findById(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> workerService.handle(event))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Incident not found");
    }

    private NotificationRequestedEvent event(NotificationType type, LocalDateTime occurredAt) {
        return new NotificationRequestedEvent(
                UUID.randomUUID(),
                10L,
                20L,
                30L,
                type,
                occurredAt
        );
    }

    private Incident incident() {
        return Incident.builder()
                .status(IncidentStatus.OPEN)
                .startedAt(LocalDateTime.of(2026, 7, 1, 9, 55))
                .lastErrorMessage("HTTP 500")
                .build();
    }
}
