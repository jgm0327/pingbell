package com.monit.pingbell.check.scheduler.event;

import com.monit.pingbell.incident.service.IncidentDetectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "pingbell.check.dispatch-mode", havingValue = "kafka")
public class HealthCheckCompletedConsumer {
    private final IncidentDetectionService incidentDetectionService;
    private final Clock clock;

    @KafkaListener(
            topics = "${pingbell.check.kafka.topic.health-check-completed}",
            groupId = "${pingbell.check.kafka.consumer.group-id}"
    )
    public void consume(HealthCheckCompletedEvent event) {
        try {
            incidentDetectionService.detectFromCompletedEvent(event, LocalDateTime.now(clock));
        } catch (Exception e) {
            log.error("Failed to consume HealthCheckCompleted event. eventId={}, requestEventId={}, monitorId={}, checkResultId={}",
                    event.eventId(), event.requestEventId(), event.monitorId(), event.checkResultId(), e);
            throw e;
        }
    }
}
