package com.monit.pingbell.notification.service;

import com.monit.pingbell.notification.config.NotificationRetryProperties;
import com.monit.pingbell.notification.type.NotificationType;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationRetryPolicyTest {

    private final NotificationRetryPolicy retryPolicy = new NotificationRetryPolicy(new NotificationRetryProperties());

    @Test
    void incidentOpenUsesImmediateRetryAndFastBackoff() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 8, 10, 0);

        assertThat(retryPolicy.shouldRetryImmediatelyOnInitialFailure(NotificationType.INCIDENT_OPEN)).isTrue();
        assertThat(retryPolicy.maxRetryCount(NotificationType.INCIDENT_OPEN)).isEqualTo(4);
        assertThat(retryPolicy.nextRetryAt(NotificationType.INCIDENT_OPEN, 1, now)).isEqualTo(now.plusSeconds(30));
        assertThat(retryPolicy.nextRetryAt(NotificationType.INCIDENT_OPEN, 2, now)).isEqualTo(now.plusMinutes(1));
        assertThat(retryPolicy.nextRetryAt(NotificationType.INCIDENT_OPEN, 3, now)).isEqualTo(now.plusMinutes(3));
    }

    @Test
    void incidentResolvedUsesSlowerBackoffWithoutImmediateRetry() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 8, 10, 0);

        assertThat(retryPolicy.shouldRetryImmediatelyOnInitialFailure(NotificationType.INCIDENT_RESOLVED)).isFalse();
        assertThat(retryPolicy.maxRetryCount(NotificationType.INCIDENT_RESOLVED)).isEqualTo(2);
        assertThat(retryPolicy.nextRetryAt(NotificationType.INCIDENT_RESOLVED, 0, now)).isEqualTo(now.plusMinutes(1));
        assertThat(retryPolicy.nextRetryAt(NotificationType.INCIDENT_RESOLVED, 1, now)).isEqualTo(now.plusMinutes(5));
        assertThat(retryPolicy.nextRetryAt(NotificationType.INCIDENT_RESOLVED, 2, now)).isEqualTo(now.plusMinutes(5));
    }
}
