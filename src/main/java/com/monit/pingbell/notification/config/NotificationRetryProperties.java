package com.monit.pingbell.notification.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "pingbell.notification.retry")
public class NotificationRetryProperties {

    private int schedulerIntervalMs = 30_000;
    private TypePolicy incidentOpen = TypePolicy.incidentOpenDefaults();
    private TypePolicy incidentResolved = TypePolicy.incidentResolvedDefaults();

    @Getter
    @Setter
    public static class TypePolicy {
        private boolean immediateRetry;
        private int maxRetryCount;
        private List<Duration> backoffs = new ArrayList<>();

        private static TypePolicy incidentOpenDefaults() {
            TypePolicy policy = new TypePolicy();
            policy.immediateRetry = true;
            policy.maxRetryCount = 4;
            policy.backoffs = new ArrayList<>(List.of(
                    Duration.ofSeconds(30),
                    Duration.ofMinutes(1),
                    Duration.ofMinutes(3)
            ));
            return policy;
        }

        private static TypePolicy incidentResolvedDefaults() {
            TypePolicy policy = new TypePolicy();
            policy.immediateRetry = false;
            policy.maxRetryCount = 2;
            policy.backoffs = new ArrayList<>(List.of(
                    Duration.ofMinutes(1),
                    Duration.ofMinutes(5)
            ));
            return policy;
        }
    }
}
