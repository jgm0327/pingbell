package com.monit.pingbell.global.config;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

@DirtiesContext
@SpringJUnitConfig(classes = {
        KafkaConsumerErrorHandlerConfig.class,
        KafkaConsumerFailurePolicy.class,
        KafkaConsumerDlqFlowTest.KafkaTestConfig.class,
        KafkaConsumerDlqFlowTest.FailingListener.class
})
@EmbeddedKafka(partitions = 1, topics = {
        KafkaConsumerDlqFlowTest.SOURCE_TOPIC,
        KafkaConsumerDlqFlowTest.DLQ_TOPIC
})
@TestPropertySource(properties = {
        "pingbell.check.dispatch-mode=kafka",
        "pingbell.kafka.consumer.retry.backoff-interval-ms=0",
        "pingbell.kafka.consumer.retry.max-retries=2"
})
class KafkaConsumerDlqFlowTest {
    static final String SOURCE_TOPIC = "pingbell.test.consumer.requested";
    static final String DLQ_TOPIC = SOURCE_TOPIC + ".dlq";

    @Autowired
    private KafkaTemplate<Object, Object> kafkaTemplate;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafka;

    @Autowired
    private FailingListener failingListener;

    @Test
    void nonRetryableFailureMovesToDlqWithoutRetry() {
        TestDlqEvent event = new TestDlqEvent(UUID.randomUUID().toString(), "nonRetryable");

        kafkaTemplate.send(SOURCE_TOPIC, event.eventId(), event).join();

        ConsumerRecord<String, TestDlqEvent> dlqRecord = readDlqRecord(event.eventId());

        assertThat(dlqRecord.value()).isEqualTo(event);
        assertThat(failingListener.attempts(event.eventId())).isEqualTo(1);
        assertDlqHeaders(dlqRecord.headers(), IllegalArgumentException.class);
    }

    @Test
    void retryableFailureMovesToDlqAfterRetriesAreExhausted() {
        TestDlqEvent event = new TestDlqEvent(UUID.randomUUID().toString(), "retryable");

        kafkaTemplate.send(SOURCE_TOPIC, event.eventId(), event).join();

        ConsumerRecord<String, TestDlqEvent> dlqRecord = readDlqRecord(event.eventId());

        assertThat(dlqRecord.value()).isEqualTo(event);
        assertThat(failingListener.attempts(event.eventId())).isEqualTo(3);
        assertDlqHeaders(dlqRecord.headers(), IllegalStateException.class);
    }

    private ConsumerRecord<String, TestDlqEvent> readDlqRecord(String eventId) {
        Map<String, Object> props = KafkaTestUtils.consumerProps(
                "dlq-assert-" + eventId,
                "false",
                embeddedKafka
        );
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        try (Consumer<String, TestDlqEvent> consumer = new DefaultKafkaConsumerFactory<>(
                props,
                new StringDeserializer(),
                testEventDeserializer()
        ).createConsumer()) {
            embeddedKafka.consumeFromAnEmbeddedTopic(consumer, DLQ_TOPIC);
            long deadline = System.currentTimeMillis() + Duration.ofSeconds(10).toMillis();
            while (System.currentTimeMillis() < deadline) {
                ConsumerRecords<String, TestDlqEvent> records = consumer.poll(Duration.ofMillis(250));
                for (ConsumerRecord<String, TestDlqEvent> record : records.records(DLQ_TOPIC)) {
                    if (eventId.equals(record.value().eventId())) {
                        return record;
                    }
                }
            }
        }
        fail("DLQ record was not found. eventId=" + eventId);
        return null;
    }

    private void assertDlqHeaders(Headers headers, Class<? extends Exception> exceptionClass) {
        assertThat(header(headers, "pingbell-dlq-original-topic")).isEqualTo(SOURCE_TOPIC);
        assertThat(header(headers, "pingbell-dlq-original-partition")).isEqualTo("0");
        assertThat(header(headers, "pingbell-dlq-original-offset")).isNotBlank();
        assertThat(header(headers, "pingbell-dlq-exception")).isEqualTo(exceptionClass.getName());
        assertThat(headers.lastHeader("kafka_exception-message")).isNull();
        assertThat(headers.lastHeader("kafka_exception-stacktrace")).isNull();
    }

    private String header(Headers headers, String name) {
        return new String(headers.lastHeader(name).value(), StandardCharsets.UTF_8);
    }

    private JsonDeserializer<TestDlqEvent> testEventDeserializer() {
        JsonDeserializer<TestDlqEvent> deserializer = new JsonDeserializer<>(TestDlqEvent.class);
        deserializer.addTrustedPackages("*");
        deserializer.ignoreTypeHeaders();
        return deserializer;
    }

    record TestDlqEvent(String eventId, String failureType) {
    }

    static class FailingListener {
        private final Map<String, AtomicInteger> attempts = new ConcurrentHashMap<>();

        @KafkaListener(topics = SOURCE_TOPIC, groupId = "dlq-flow-listener")
        void consume(TestDlqEvent event) {
            attempts.computeIfAbsent(event.eventId(), key -> new AtomicInteger()).incrementAndGet();
            if ("nonRetryable".equals(event.failureType())) {
                throw new IllegalArgumentException("invalid event");
            }
            throw new IllegalStateException("temporary event failure");
        }

        int attempts(String eventId) {
            return attempts.getOrDefault(eventId, new AtomicInteger()).get();
        }
    }

    @EnableKafka
    @Configuration
    static class KafkaTestConfig {

        @Bean
        ProducerFactory<Object, Object> producerFactory(EmbeddedKafkaBroker embeddedKafka) {
            Map<String, Object> props = KafkaTestUtils.producerProps(embeddedKafka);
            props.put(org.apache.kafka.clients.producer.ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
            props.put(org.apache.kafka.clients.producer.ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
            props.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, false);
            return new DefaultKafkaProducerFactory<>(props);
        }

        @Bean
        KafkaTemplate<Object, Object> kafkaTemplate(ProducerFactory<Object, Object> producerFactory) {
            return new KafkaTemplate<>(producerFactory);
        }

        @Bean
        ConsumerFactory<String, TestDlqEvent> consumerFactory(EmbeddedKafkaBroker embeddedKafka) {
            Map<String, Object> props = KafkaTestUtils.consumerProps("dlq-flow-listener", "false", embeddedKafka);
            props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
            return new DefaultKafkaConsumerFactory<>(
                    props,
                    new StringDeserializer(),
                    testEventDeserializer()
            );
        }

        @Bean
        ConcurrentKafkaListenerContainerFactory<String, TestDlqEvent> kafkaListenerContainerFactory(
                ConsumerFactory<String, TestDlqEvent> consumerFactory,
                CommonErrorHandler errorHandler
        ) {
            ConcurrentKafkaListenerContainerFactory<String, TestDlqEvent> factory =
                    new ConcurrentKafkaListenerContainerFactory<>();
            factory.setConsumerFactory(consumerFactory);
            factory.setCommonErrorHandler(errorHandler);
            return factory;
        }

        private JsonDeserializer<TestDlqEvent> testEventDeserializer() {
            JsonDeserializer<TestDlqEvent> deserializer = new JsonDeserializer<>(TestDlqEvent.class);
            deserializer.addTrustedPackages("*");
            deserializer.ignoreTypeHeaders();
            return deserializer;
        }
    }
}
