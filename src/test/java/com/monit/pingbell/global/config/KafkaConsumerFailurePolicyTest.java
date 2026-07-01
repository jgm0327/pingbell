package com.monit.pingbell.global.config;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaConsumerFailurePolicyTest {

    private final KafkaConsumerFailurePolicy policy = new KafkaConsumerFailurePolicy();

    @Test
    void dlqTopicUsesSourceTopicSuffix() {
        assertThat(policy.dlqTopic("pingbell.health-check.requested"))
                .isEqualTo("pingbell.health-check.requested.dlq");
    }

    @Test
    void classifiesRetryableFailure() {
        assertThat(policy.isRetryable(new IllegalStateException("temporary failure"))).isTrue();
    }

    @Test
    void classifiesNonRetryableFailures() {
        assertThat(policy.isRetryable(new IllegalArgumentException("bad payload"))).isFalse();
        assertThat(policy.isRetryable(new ClassCastException("bad type"))).isFalse();
        assertThat(policy.isRetryable(new NoSuchElementException("missing data"))).isFalse();
    }

    @Test
    void exposesNonRetryableExceptionsForDefaultErrorHandler() {
        assertThat(Arrays.asList(policy.nonRetryableExceptions()))
                .containsExactly(
                        IllegalArgumentException.class,
                        ClassCastException.class,
                        NoSuchElementException.class
                );
    }
}
