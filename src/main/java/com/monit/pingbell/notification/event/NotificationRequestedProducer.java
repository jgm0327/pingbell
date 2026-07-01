package com.monit.pingbell.notification.event;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class NotificationRequestedProducer {
    private final KafkaTemplate<String, NotificationRequestedEvent> kafkaTemplate;
    private final String topicName;

    public NotificationRequestedProducer(
            KafkaTemplate<String, NotificationRequestedEvent> kafkaTemplate,
            @Value("${pingbell.check.kafka.topic.notification-requested}") String topicName
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.topicName = topicName;
    }

    public void publish(NotificationRequestedEvent event) {
        try {
            kafkaTemplate.send(topicName, event.incidentId().toString(), event).join();
        } catch (Exception e) {
            log.error("Failed to publish NotificationRequested event. eventId={}, incidentId={}, notificationType={}",
                    event.eventId(), event.incidentId(), event.notificationType(), e);
            throw new IllegalStateException("Failed to publish NotificationRequested event.", e);
        }
    }
}
