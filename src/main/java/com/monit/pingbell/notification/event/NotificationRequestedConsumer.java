package com.monit.pingbell.notification.event;

import com.monit.pingbell.notification.service.NotificationWorkerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "pingbell.check.dispatch-mode", havingValue = "kafka")
public class NotificationRequestedConsumer {
    private final NotificationWorkerService notificationWorkerService;

    @KafkaListener(
            topics = "${pingbell.check.kafka.topic.notification-requested}",
            groupId = "${pingbell.notification.kafka.consumer.group-id}"
    )
    public void consume(NotificationRequestedEvent event) {
        try {
            notificationWorkerService.handle(event);
        } catch (Exception e) {
            log.error("Failed to consume NotificationRequested event. eventId={}, incidentId={}, notificationType={}",
                    event.eventId(), event.incidentId(), event.notificationType(), e);
            throw e;
        }
    }
}
