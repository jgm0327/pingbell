package com.monit.pingbell.check.scheduler.event;

import com.monit.pingbell.check.service.CheckService;
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
public class HealthCheckRequestedConsumer {
    private final CheckService checkService;
    private final HealthCheckCompletedProducer completedProducer;
    private final Clock clock;

    @KafkaListener(
            topics = "${pingbell.check.kafka.topic.health-check-requested}",
            groupId = "${pingbell.check.kafka.consumer.group-id}"
    )
    public void consume(HealthCheckRequestedEvent event) {
        try {
            checkService.handleRequestedCheck(event, LocalDateTime.now(clock))
                    .ifPresent(completedProducer::publish);
        } catch (Exception e) {
            log.error("Failed to consume HealthCheckRequested event. eventId={}, monitorId={}",
                    event.eventId(), event.monitorId(), e);
            throw e;
        }
    }
}
