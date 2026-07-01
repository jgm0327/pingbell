package com.monit.pingbell.check.scheduler.event;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaTopicConfigTest {

    private final KafkaTopicConfig config = new KafkaTopicConfig();

    @Test
    void createsDlqTopicsBesideSourceTopics() {
        assertThat(config.healthCheckRequestedDlqTopic("pingbell.health-check.requested").name())
                .isEqualTo("pingbell.health-check.requested.dlq");
        assertThat(config.healthCheckCompletedDlqTopic("pingbell.health-check.completed").name())
                .isEqualTo("pingbell.health-check.completed.dlq");
        assertThat(config.notificationRequestedDlqTopic("pingbell.notification.requested").name())
                .isEqualTo("pingbell.notification.requested.dlq");
    }
}
