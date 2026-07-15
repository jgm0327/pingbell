package com.monit.pingbell.notification.service;

import com.monit.pingbell.incident.domain.Incident;
import com.monit.pingbell.notification.event.NotificationRequestedEvent;
import com.monit.pingbell.notification.event.NotificationRequestedProducer;
import com.monit.pingbell.notification.type.NotificationType;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "pingbell.check.dispatch-mode", havingValue = "kafka")
public class KafkaNotificationDispatchService implements NotificationDispatchService {
    private final NotificationRequestedProducer producer;

    @Override
    public void dispatch(Incident incident, NotificationType type, LocalDateTime occurredAt) {
        NotificationRequestedEvent event = NotificationRequestedEvent.from(incident, type, occurredAt);
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            producer.publish(event);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                producer.publish(event);
            }
        });
    }
}
