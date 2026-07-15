package com.monit.pingbell.notification.service;

import com.monit.pingbell.incident.domain.Incident;
import com.monit.pingbell.incident.domain.IncidentStatus;
import com.monit.pingbell.member.domain.Member;
import com.monit.pingbell.monitor.domain.Monitor;
import com.monit.pingbell.monitor.domain.MonitorStatus;
import com.monit.pingbell.notification.event.NotificationRequestedEvent;
import com.monit.pingbell.notification.event.NotificationRequestedProducer;
import com.monit.pingbell.notification.type.NotificationType;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class KafkaNotificationDispatchServiceTest {

    @Test
    void dispatchPublishesNotificationRequestedEvent() {
        NotificationRequestedProducer producer = mock(NotificationRequestedProducer.class);
        KafkaNotificationDispatchService dispatchService = new KafkaNotificationDispatchService(producer);
        Incident incident = incident();
        LocalDateTime occurredAt = LocalDateTime.of(2026, 7, 1, 10, 0);

        dispatchService.dispatch(incident, NotificationType.INCIDENT_OPEN, occurredAt);

        ArgumentCaptor<NotificationRequestedEvent> captor = ArgumentCaptor.forClass(NotificationRequestedEvent.class);
        verify(producer).publish(captor.capture());
        NotificationRequestedEvent event = captor.getValue();
        assertThat(event.incidentId()).isEqualTo(10L);
        assertThat(event.monitorId()).isEqualTo(20L);
        assertThat(event.memberId()).isEqualTo(30L);
        assertThat(event.notificationType()).isEqualTo(NotificationType.INCIDENT_OPEN);
        assertThat(event.occurredAt()).isEqualTo(occurredAt);
    }

    @Test
    void dispatchPublishesAfterCommitWhenTransactionSynchronizationIsActive() {
        NotificationRequestedProducer producer = mock(NotificationRequestedProducer.class);
        KafkaNotificationDispatchService dispatchService = new KafkaNotificationDispatchService(producer);
        Incident incident = incident();
        LocalDateTime occurredAt = LocalDateTime.of(2026, 7, 1, 10, 0);

        TransactionSynchronizationManager.initSynchronization();
        try {
            dispatchService.dispatch(incident, NotificationType.INCIDENT_OPEN, occurredAt);

            verify(producer, never()).publish(org.mockito.ArgumentMatchers.any());

            for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
                synchronization.afterCommit();
            }
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }

        verify(producer).publish(org.mockito.ArgumentMatchers.any(NotificationRequestedEvent.class));
    }

    private Incident incident() {
        Member member = Member.builder()
                .email("user@example.com")
                .password("password")
                .build();
        ReflectionTestUtils.setField(member, "id", 30L);

        Monitor monitor = Monitor.builder()
                .member(member)
                .name("api")
                .url("http://localhost:9999/health")
                .intervalSeconds(30)
                .timeoutMillis(1000)
                .failureThreshold(1)
                .recoveryThreshold(1)
                .status(MonitorStatus.DOWN)
                .nextCheckAt(LocalDateTime.of(2026, 7, 1, 9, 59))
                .build();
        ReflectionTestUtils.setField(monitor, "id", 20L);

        Incident incident = Incident.builder()
                .monitor(monitor)
                .status(IncidentStatus.OPEN)
                .startedAt(LocalDateTime.of(2026, 7, 1, 9, 55))
                .lastErrorMessage("HTTP 500")
                .build();
        ReflectionTestUtils.setField(incident, "id", 10L);
        return incident;
    }
}
