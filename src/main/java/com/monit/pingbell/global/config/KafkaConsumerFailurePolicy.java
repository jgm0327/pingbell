package com.monit.pingbell.global.config;

import org.springframework.stereotype.Component;

import java.util.NoSuchElementException;

@Component
public class KafkaConsumerFailurePolicy {
    private static final String DLQ_SUFFIX = ".dlq";

    public String dlqTopic(String sourceTopic) {
        return sourceTopic + DLQ_SUFFIX;
    }

    public boolean isRetryable(Exception exception) {
        return !(exception instanceof IllegalArgumentException
                || exception instanceof ClassCastException
                || exception instanceof NoSuchElementException);
    }

    @SuppressWarnings("unchecked")
    public Class<? extends Exception>[] nonRetryableExceptions() {
        return new Class[] {
                IllegalArgumentException.class,
                ClassCastException.class,
                NoSuchElementException.class
        };
    }
}
