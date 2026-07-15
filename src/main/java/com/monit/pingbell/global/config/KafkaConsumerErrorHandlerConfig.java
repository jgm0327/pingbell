package com.monit.pingbell.global.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

import java.nio.charset.StandardCharsets;

@Slf4j
@Configuration
@ConditionalOnProperty(name = "pingbell.check.dispatch-mode", havingValue = "kafka")
public class KafkaConsumerErrorHandlerConfig {

    @Bean
    public CommonErrorHandler kafkaConsumerErrorHandler(
            KafkaTemplate<Object, Object> kafkaTemplate,
            KafkaConsumerFailurePolicy failurePolicy,
            @Value("${pingbell.kafka.consumer.retry.backoff-interval-ms}") long backoffIntervalMs,
            @Value("${pingbell.kafka.consumer.retry.max-retries}") long maxRetries
    ) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (record, exception) -> new TopicPartition(failurePolicy.dlqTopic(record.topic()), record.partition())
        );
        recoverer.setAppendOriginalHeaders(false);
        recoverer.setExceptionHeadersCreator((headers, exception, isKey, headerNames) -> {
        });
        recoverer.setHeadersFunction((record, exception) -> {
            RecordHeaders headers = new RecordHeaders();
            headers.add("pingbell-dlq-original-topic", record.topic().getBytes(StandardCharsets.UTF_8));
            headers.add("pingbell-dlq-original-partition", String.valueOf(record.partition()).getBytes(StandardCharsets.UTF_8));
            headers.add("pingbell-dlq-original-offset", String.valueOf(record.offset()).getBytes(StandardCharsets.UTF_8));
            headers.add("pingbell-dlq-exception", rootCause(exception).getClass().getName().getBytes(StandardCharsets.UTF_8));
            return headers;
        });

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, new FixedBackOff(backoffIntervalMs, maxRetries));
        errorHandler.addNotRetryableExceptions(failurePolicy.nonRetryableExceptions());
        errorHandler.setRetryListeners((record, exception, deliveryAttempt) ->
                log.warn(
                        "Retry Kafka consumer event. topic={}, partition={}, offset={}, attempt={}, exception={}",
                        record.topic(),
                        record.partition(),
                        record.offset(),
                        deliveryAttempt,
                        exception.getClass().getSimpleName()
                )
        );
        return errorHandler;
    }

    private Throwable rootCause(Exception exception) {
        Throwable cause = exception;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause;
    }
}
