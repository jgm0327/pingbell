package com.monit.pingbell.global.dlq;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.monit.pingbell.check.scheduler.event.HealthCheckCompletedEvent;
import com.monit.pingbell.check.scheduler.event.HealthCheckRequestedEvent;
import com.monit.pingbell.notification.event.NotificationRequestedEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
public class DlqRecordReader {

    private static final Duration POLL_TIMEOUT = Duration.ofSeconds(3);

    private final ObjectMapper objectMapper;
    private final DlqRecordInspector recordInspector;
    private final String bootstrapServers;
    private final String healthCheckRequestedTopic;
    private final String healthCheckCompletedTopic;
    private final String notificationRequestedTopic;

    public DlqRecordReader(
            ObjectMapper objectMapper,
            DlqRecordInspector recordInspector,
            @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers,
            @Value("${pingbell.check.kafka.topic.health-check-requested}") String healthCheckRequestedTopic,
            @Value("${pingbell.check.kafka.topic.health-check-completed}") String healthCheckCompletedTopic,
            @Value("${pingbell.check.kafka.topic.notification-requested}") String notificationRequestedTopic
    ) {
        this.objectMapper = objectMapper;
        this.recordInspector = recordInspector;
        this.bootstrapServers = bootstrapServers;
        this.healthCheckRequestedTopic = healthCheckRequestedTopic;
        this.healthCheckCompletedTopic = healthCheckCompletedTopic;
        this.notificationRequestedTopic = notificationRequestedTopic;
    }

    public Object read(String topic, int partition, long offset) {
        TopicPartition topicPartition = new TopicPartition(topic, partition);
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(consumerProperties())) {
            consumer.assign(List.of(topicPartition));
            consumer.seek(topicPartition, offset);
            return findRecordValue(consumer, topicPartition, offset)
                    .map(value -> deserialize(topic, value))
                    .orElseThrow(() -> new IllegalArgumentException(
                            "DLQ record not found. topic=%s, partition=%d, offset=%d".formatted(topic, partition, offset)
                    ));
        }
    }

    public List<DlqRecordSnapshot> readList(String topic, int maxRecords) {
        if (maxRecords < 1) {
            throw new IllegalArgumentException("maxRecords must be greater than 0");
        }

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(consumerProperties())) {
            List<TopicPartition> topicPartitions = partitions(consumer, topic);
            consumer.assign(topicPartitions);
            consumer.seekToBeginning(topicPartitions);
            Map<TopicPartition, Long> endOffsets = consumer.endOffsets(topicPartitions);

            return readUntilLimitOrEnd(consumer, endOffsets, maxRecords);
        }
    }

    private List<TopicPartition> partitions(KafkaConsumer<String, String> consumer, String topic) {
        List<PartitionInfo> partitionInfos = consumer.partitionsFor(topic);
        if (partitionInfos == null || partitionInfos.isEmpty()) {
            throw new IllegalArgumentException("DLQ topic has no partitions. topic=" + topic);
        }
        return partitionInfos.stream()
                .map(partitionInfo -> new TopicPartition(partitionInfo.topic(), partitionInfo.partition()))
                .toList();
    }

    private List<DlqRecordSnapshot> readUntilLimitOrEnd(
            KafkaConsumer<String, String> consumer,
            Map<TopicPartition, Long> endOffsets,
            int maxRecords
    ) {
        List<DlqRecordSnapshot> snapshots = new java.util.ArrayList<>();
        while (snapshots.size() < maxRecords && hasRemainingRecords(consumer, endOffsets)) {
            var records = consumer.poll(POLL_TIMEOUT);
            if (records.isEmpty()) {
                break;
            }
            for (var record : records) {
                if (snapshots.size() >= maxRecords) {
                    break;
                }
                snapshots.add(toSnapshot(record.topic(), record.partition(), record.offset(), record.value()));
            }
        }
        return snapshots;
    }

    private boolean hasRemainingRecords(KafkaConsumer<String, String> consumer, Map<TopicPartition, Long> endOffsets) {
        return endOffsets.entrySet().stream()
                .anyMatch(entry -> consumer.position(entry.getKey()) < entry.getValue());
    }

    private DlqRecordSnapshot toSnapshot(String topic, int partition, long offset, String value) {
        if (value == null) {
            return DlqRecordSnapshot.unreadable(topic, partition, offset, "TOMBSTONE", "DLQ record value is null");
        }
        try {
            Object payload = deserialize(topic, value);
            return DlqRecordSnapshot.readable(
                    topic,
                    partition,
                    offset,
                    recordInspector.payloadType(payload),
                    recordInspector.primaryIds(payload),
                    payload
            );
        } catch (RuntimeException exception) {
            return DlqRecordSnapshot.unreadable(topic, partition, offset, "INVALID_PAYLOAD", exception.getMessage());
        }
    }

    private Optional<String> findRecordValue(
            KafkaConsumer<String, String> consumer,
            TopicPartition topicPartition,
            long offset
    ) {
        var records = consumer.poll(POLL_TIMEOUT);
        for (var record : records.records(topicPartition)) {
            if (record.offset() == offset) {
                return Optional.ofNullable(record.value());
            }
        }
        return Optional.empty();
    }

    private Object deserialize(String dlqTopic, String value) {
        try {
            if (dlqTopic.equals(dlqTopicName(healthCheckRequestedTopic))) {
                return objectMapper.readValue(value, HealthCheckRequestedEvent.class);
            }
            if (dlqTopic.equals(dlqTopicName(healthCheckCompletedTopic))) {
                return objectMapper.readValue(value, HealthCheckCompletedEvent.class);
            }
            if (dlqTopic.equals(dlqTopicName(notificationRequestedTopic))) {
                return objectMapper.readValue(value, NotificationRequestedEvent.class);
            }
            throw new IllegalArgumentException("Unsupported DLQ topic: " + dlqTopic);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Invalid DLQ payload schema. topic=" + dlqTopic, exception);
        }
    }

    private String dlqTopicName(String sourceTopic) {
        return sourceTopic + ".dlq";
    }

    private Map<String, Object> consumerProperties() {
        return Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                ConsumerConfig.GROUP_ID_CONFIG, "pingbell-dlq-dry-run-" + UUID.randomUUID(),
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false,
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "none"
        );
    }
}
