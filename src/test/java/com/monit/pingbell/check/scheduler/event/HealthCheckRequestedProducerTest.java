package com.monit.pingbell.check.scheduler.event;

import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HealthCheckRequestedProducerTest {

    @Test
    void publishSendsMessageWithMonitorIdKey() {
        KafkaTemplate<String, HealthCheckRequestedEvent> kafkaTemplate = kafkaTemplate();
        HealthCheckRequestedProducer producer = new HealthCheckRequestedProducer(kafkaTemplate, "health-topic");
        HealthCheckRequestedEvent event = event();

        CompletableFuture<SendResult<String, HealthCheckRequestedEvent>> success = CompletableFuture.completedFuture(null);
        when(kafkaTemplate.send("health-topic", "10", event)).thenReturn(success);

        producer.publish(event);

        verify(kafkaTemplate).send("health-topic", "10", event);
    }

    @Test
    void publishThrowsWhenKafkaSendFails() {
        KafkaTemplate<String, HealthCheckRequestedEvent> kafkaTemplate = kafkaTemplate();
        HealthCheckRequestedProducer producer = new HealthCheckRequestedProducer(kafkaTemplate, "health-topic");
        HealthCheckRequestedEvent event = event();
        CompletableFuture<SendResult<String, HealthCheckRequestedEvent>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new IllegalStateException("kafka down"));

        when(kafkaTemplate.send("health-topic", "10", event)).thenReturn(failed);

        assertThatThrownBy(() -> producer.publish(event))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to publish HealthCheckRequested event");
    }

    private HealthCheckRequestedEvent event() {
        return new HealthCheckRequestedEvent(
                UUID.randomUUID(),
                LocalDateTime.of(2026, 6, 30, 10, 0),
                10L,
                20L,
                1000,
                30,
                LocalDateTime.of(2026, 6, 30, 9, 59),
                "SCHEDULER"
        );
    }

    @SuppressWarnings("unchecked")
    private KafkaTemplate<String, HealthCheckRequestedEvent> kafkaTemplate() {
        return mock(KafkaTemplate.class);
    }
}
