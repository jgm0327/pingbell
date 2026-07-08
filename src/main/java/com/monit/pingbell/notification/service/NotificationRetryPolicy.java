package com.monit.pingbell.notification.service;

import com.monit.pingbell.notification.config.NotificationRetryProperties;
import com.monit.pingbell.notification.type.NotificationType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class NotificationRetryPolicy {

    private final NotificationRetryProperties properties;

    public boolean shouldRetryImmediatelyOnInitialFailure(NotificationType type) {
        return policy(type).isImmediateRetry();
    }

    public int maxRetryCount(NotificationType type) {
        return policy(type).getMaxRetryCount();
    }

    public LocalDateTime nextRetryAt(NotificationType type, int retryCount, LocalDateTime now) {
        List<Duration> backoffs = policy(type).getBackoffs();
        if (backoffs.isEmpty()) {
            return now;
        }

        int backoffIndex = policy(type).isImmediateRetry() ? retryCount - 1 : retryCount;
        backoffIndex = Math.max(0, backoffIndex);
        if (backoffIndex >= backoffs.size()) {
            backoffIndex = backoffs.size() - 1;
        }
        return now.plus(backoffs.get(backoffIndex));
    }

    private NotificationRetryProperties.TypePolicy policy(NotificationType type) {
        if (type == NotificationType.INCIDENT_OPEN) {
            return properties.getIncidentOpen();
        }
        return properties.getIncidentResolved();
    }
}
