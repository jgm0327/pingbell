package com.monit.pingbell.global.dlq;

import com.monit.pingbell.check.domain.CheckStatus;
import com.monit.pingbell.check.scheduler.event.HealthCheckCompletedEvent;
import com.monit.pingbell.check.scheduler.event.HealthCheckRequestedEvent;
import com.monit.pingbell.notification.event.NotificationRequestedEvent;
import com.monit.pingbell.notification.type.NotificationType;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DlqRecordInspectorTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 1, 10, 0);

    private final DlqRecordInspector inspector = new DlqRecordInspector();

    @Test
    void primaryIdsForHealthCheckRequestedUseOnlyOperationalIds() {
        HealthCheckRequestedEvent event = new HealthCheckRequestedEvent(
                UUID.randomUUID(),
                NOW,
                10L,
                20L,
                1000,
                30,
                NOW,
                "SCHEDULER"
        );

        String primaryIds = inspector.primaryIds(event);

        assertThat(inspector.payloadType(event)).isEqualTo("HealthCheckRequestedEvent");
        assertThat(primaryIds).contains("monitorId=10", "memberId=20");
        assertThat(primaryIds).doesNotContain("http://", "@");
    }

    @Test
    void primaryIdsForHealthCheckCompletedIncludeCheckResultId() {
        HealthCheckCompletedEvent event = new HealthCheckCompletedEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                10L,
                20L,
                30L,
                CheckStatus.SUCCESS,
                200,
                100L,
                null,
                NOW
        );

        assertThat(inspector.primaryIds(event)).contains("monitorId=10", "memberId=20", "checkResultId=30");
    }

    @Test
    void primaryIdsForNotificationRequestedIncludeIncidentAndType() {
        NotificationRequestedEvent event = new NotificationRequestedEvent(
                UUID.randomUUID(),
                40L,
                10L,
                20L,
                NotificationType.INCIDENT_OPEN,
                NOW
        );

        String primaryIds = inspector.primaryIds(event);

        assertThat(primaryIds).contains(
                "incidentId=40",
                "monitorId=10",
                "memberId=20",
                "notificationType=INCIDENT_OPEN"
        );
        assertThat(primaryIds).doesNotContain("http://", "@");
    }
}
