package com.monit.pingbell.check.scheduler.event;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
@ConditionalOnProperty(name = "pingbell.check.dispatch-mode", havingValue = "kafka")
public class KafkaTopicConfig {

    @Bean
    public NewTopic healthCheckRequestedTopic(
            @Value("${pingbell.check.kafka.topic.health-check-requested}") String topicName
    ) {
        return TopicBuilder.name(topicName)
                .partitions(1)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic healthCheckCompletedTopic(
            @Value("${pingbell.check.kafka.topic.health-check-completed}") String topicName
    ) {
        return TopicBuilder.name(topicName)
                .partitions(1)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic notificationRequestedTopic(
            @Value("${pingbell.check.kafka.topic.notification-requested}") String topicName
    ) {
        return TopicBuilder.name(topicName)
                .partitions(1)
                .replicas(1)
                .build();
    }
}
