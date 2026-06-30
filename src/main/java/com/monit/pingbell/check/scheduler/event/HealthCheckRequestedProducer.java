package com.monit.pingbell.check.scheduler.event;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class HealthCheckRequestedProducer {
    private final KafkaTemplate<String, HealthCheckRequestedEvent> kafkaTemplate;
    private final String topicName;

    public HealthCheckRequestedProducer(
            KafkaTemplate<String, HealthCheckRequestedEvent> kafkaTemplate,
            @Value("${pingbell.check.kafka.topic.health-check-requested}") String topicName
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.topicName = topicName;
    }

    public void publish(HealthCheckRequestedEvent event) {
        try {
            kafkaTemplate.send(topicName, event.monitorId().toString(), event).join();
        } catch (Exception e) {
            log.error("Failed to publish HealthCheckRequested event. eventId={}, monitorId={}",
                    event.eventId(), event.monitorId(), e);
            throw new IllegalStateException("Failed to publish HealthCheckRequested event.", e);
        }
    }
}
