package com.monit.pingbell.check.scheduler.event;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class HealthCheckCompletedProducer {
    private final KafkaTemplate<String, HealthCheckCompletedEvent> kafkaTemplate;
    private final String topicName;

    public HealthCheckCompletedProducer(
            KafkaTemplate<String, HealthCheckCompletedEvent> kafkaTemplate,
            @Value("${pingbell.check.kafka.topic.health-check-completed}") String topicName
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.topicName = topicName;
    }

    public void publish(HealthCheckCompletedEvent event) {
        try {
            kafkaTemplate.send(topicName, event.monitorId().toString(), event).join();
        } catch (Exception e) {
            log.error("Failed to publish HealthCheckCompleted event. eventId={}, requestEventId={}, monitorId={}, checkResultId={}",
                    event.eventId(), event.requestEventId(), event.monitorId(), event.checkResultId(), e);
            throw new IllegalStateException("Failed to publish HealthCheckCompleted event.", e);
        }
    }
}
