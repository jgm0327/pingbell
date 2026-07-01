package com.monit.pingbell.global.dlq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.monit.pingbell.check.scheduler.event.HealthCheckRequestedEvent;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@DirtiesContext
@SpringJUnitConfig(classes = DlqRecordReaderEmbeddedKafkaTest.KafkaTestConfig.class)
@EmbeddedKafka(partitions = 1, topics = DlqRecordReaderEmbeddedKafkaTest.DLQ_TOPIC)
class DlqRecordReaderEmbeddedKafkaTest {

    private static final String SOURCE_TOPIC = "pingbell.health-check.requested";
    static final String DLQ_TOPIC = SOURCE_TOPIC + ".dlq";
    private static final String HEALTH_CHECK_COMPLETED_TOPIC = "pingbell.health-check.completed";
    private static final String NOTIFICATION_REQUESTED_TOPIC = "pingbell.notification.requested";
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 1, 10, 0);

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafka;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void readListReadsKafkaRecordsAndConvertsUnreadablePayloadsToSnapshots() throws Exception {
        HealthCheckRequestedEvent firstEvent = requestedEvent(10L, 20L);
        HealthCheckRequestedEvent secondEvent = requestedEvent(11L, 21L);

        kafkaTemplate.send(DLQ_TOPIC, "10", objectMapper.writeValueAsString(firstEvent)).join();
        kafkaTemplate.send(DLQ_TOPIC, "11", objectMapper.writeValueAsString(secondEvent)).join();
        kafkaTemplate.send(DLQ_TOPIC, "invalid", "{\"eventId\":").join();
        kafkaTemplate.send(DLQ_TOPIC, "tombstone", null).join();
        kafkaTemplate.flush();

        DlqRecordReader reader = reader();

        List<DlqRecordSnapshot> limitedRecords = reader.readList(DLQ_TOPIC, 2);
        assertThat(limitedRecords)
                .extracting(DlqRecordSnapshot::offset)
                .containsExactly(0L, 1L);

        List<DlqRecordSnapshot> records = reader.readList(DLQ_TOPIC, 10);

        assertThat(records).hasSize(4);
        assertReadableHealthCheckRequested(records.get(0), firstEvent);
        assertReadableHealthCheckRequested(records.get(1), secondEvent);
        assertUnreadable(records.get(2), 2L, "INVALID_PAYLOAD", "Invalid DLQ payload schema");
        assertUnreadable(records.get(3), 3L, "TOMBSTONE", "DLQ record value is null");

        DlqDryRunService dryRunService = Mockito.mock(DlqDryRunService.class);
        DlqBatchDryRunSummary summary = new DlqBatchDryRunService(dryRunService).verify(records.subList(2, 4));

        assertThat(summary.notReprocessableCount()).isEqualTo(2);
        assertThat(summary.records())
                .extracting(DlqBatchDryRunRecordResult::status)
                .containsExactly(DlqDryRunStatus.NOT_REPROCESSABLE, DlqDryRunStatus.NOT_REPROCESSABLE);
        verify(dryRunService, never()).verify(any());
    }

    private void assertReadableHealthCheckRequested(DlqRecordSnapshot snapshot, HealthCheckRequestedEvent event) {
        assertThat(snapshot.topic()).isEqualTo(DLQ_TOPIC);
        assertThat(snapshot.partition()).isZero();
        assertThat(snapshot.payloadType()).isEqualTo("HealthCheckRequestedEvent");
        assertThat(snapshot.primaryIds())
                .contains("eventId=" + event.eventId())
                .contains("monitorId=" + event.monitorId())
                .contains("memberId=" + event.memberId());
        assertThat(snapshot.payload()).isEqualTo(event);
        assertThat(snapshot.readError()).isNull();
    }

    private void assertUnreadable(DlqRecordSnapshot snapshot, long offset, String payloadType, String readError) {
        assertThat(snapshot.topic()).isEqualTo(DLQ_TOPIC);
        assertThat(snapshot.partition()).isZero();
        assertThat(snapshot.offset()).isEqualTo(offset);
        assertThat(snapshot.payloadType()).isEqualTo(payloadType);
        assertThat(snapshot.primaryIds()).isEmpty();
        assertThat(snapshot.payload()).isNull();
        assertThat(snapshot.readError()).contains(readError);
    }

    private DlqRecordReader reader() {
        return new DlqRecordReader(
                objectMapper,
                new DlqRecordInspector(),
                embeddedKafka.getBrokersAsString(),
                SOURCE_TOPIC,
                HEALTH_CHECK_COMPLETED_TOPIC,
                NOTIFICATION_REQUESTED_TOPIC
        );
    }

    private HealthCheckRequestedEvent requestedEvent(Long monitorId, Long memberId) {
        return new HealthCheckRequestedEvent(
                UUID.randomUUID(),
                NOW,
                monitorId,
                memberId,
                1000,
                30,
                NOW,
                "SCHEDULER"
        );
    }

    @Configuration
    static class KafkaTestConfig {

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper().registerModule(new JavaTimeModule());
        }

        @Bean
        ProducerFactory<String, String> producerFactory(EmbeddedKafkaBroker embeddedKafka) {
            Map<String, Object> props = KafkaTestUtils.producerProps(embeddedKafka);
            props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
            props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
            return new DefaultKafkaProducerFactory<>(props);
        }

        @Bean
        KafkaTemplate<String, String> kafkaTemplate(ProducerFactory<String, String> producerFactory) {
            return new KafkaTemplate<>(producerFactory);
        }
    }
}
