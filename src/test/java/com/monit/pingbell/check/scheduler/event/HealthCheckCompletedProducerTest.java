package com.monit.pingbell.check.scheduler.event;

import com.monit.pingbell.check.domain.CheckStatus;
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

class HealthCheckCompletedProducerTest {

    @Test
    void publishSendsMessageWithMonitorIdKey() {
        KafkaTemplate<String, HealthCheckCompletedEvent> kafkaTemplate = kafkaTemplate();
        HealthCheckCompletedProducer producer = new HealthCheckCompletedProducer(kafkaTemplate, "completed-topic");
        HealthCheckCompletedEvent event = event();
        CompletableFuture<SendResult<String, HealthCheckCompletedEvent>> success = CompletableFuture.completedFuture(null);

        when(kafkaTemplate.send("completed-topic", "10", event)).thenReturn(success);

        producer.publish(event);

        verify(kafkaTemplate).send("completed-topic", "10", event);
    }

    @Test
    void publishThrowsWhenKafkaSendFails() {
        KafkaTemplate<String, HealthCheckCompletedEvent> kafkaTemplate = kafkaTemplate();
        HealthCheckCompletedProducer producer = new HealthCheckCompletedProducer(kafkaTemplate, "completed-topic");
        HealthCheckCompletedEvent event = event();
        CompletableFuture<SendResult<String, HealthCheckCompletedEvent>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new IllegalStateException("kafka down"));

        when(kafkaTemplate.send("completed-topic", "10", event)).thenReturn(failed);

        assertThatThrownBy(() -> producer.publish(event))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to publish HealthCheckCompleted event");
    }

    private HealthCheckCompletedEvent event() {
        return new HealthCheckCompletedEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                10L,
                20L,
                30L,
                CheckStatus.SUCCESS,
                200,
                120L,
                null,
                LocalDateTime.of(2026, 6, 30, 10, 0)
        );
    }

    @SuppressWarnings("unchecked")
    private KafkaTemplate<String, HealthCheckCompletedEvent> kafkaTemplate() {
        return mock(KafkaTemplate.class);
    }
}
