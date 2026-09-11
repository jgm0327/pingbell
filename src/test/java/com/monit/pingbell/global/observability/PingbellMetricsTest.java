package com.monit.pingbell.global.observability;

import com.monit.pingbell.incident.domain.IncidentStatus;
import com.monit.pingbell.incident.repository.IncidentRepository;
import com.monit.pingbell.notification.repository.NotificationHistoryRepository;
import com.monit.pingbell.notification.type.NotificationChannelType;
import com.monit.pingbell.notification.type.NotificationStatus;
import com.monit.pingbell.notification.type.NotificationType;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 운영 관측성 gauge(docs/operations/observability-metrics.md 12절 "가장 먼저 볼 지표")가
 * 실제로 등록되고, counter와 달리 조회 시점마다 DB 상태를 다시 반영하는지 확인한다.
 */
@ExtendWith(MockitoExtension.class)
class PingbellMetricsTest {

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

    @Mock
    private IncidentRepository incidentRepository;
    @Mock
    private NotificationHistoryRepository notificationHistoryRepository;

    @Test
    void exposesCurrentOpenIncidentCountAsAGaugeThatIsRecomputedOnEachRead() {
        when(incidentRepository.countByStatus(IncidentStatus.OPEN)).thenReturn(3L);

        PingbellMetrics metrics = new PingbellMetrics(meterRegistry, incidentRepository, notificationHistoryRepository);
        metrics.registerStateGauges();

        assertThat(meterRegistry.get("pingbell.incident.open.current").gauge().value()).isEqualTo(3.0);

        // gauge는 등록 시점 값을 캐시하지 않는다 - repository 상태가 바뀌면 다음 조회에 바로 반영돼야 한다.
        when(incidentRepository.countByStatus(IncidentStatus.OPEN)).thenReturn(1L);
        assertThat(meterRegistry.get("pingbell.incident.open.current").gauge().value()).isEqualTo(1.0);
    }

    @Test
    void exposesCurrentRetryPendingNotificationCountAsAGauge() {
        when(notificationHistoryRepository.countByStatus(NotificationStatus.RETRY_PENDING)).thenReturn(5L);

        PingbellMetrics metrics = new PingbellMetrics(meterRegistry, incidentRepository, notificationHistoryRepository);
        metrics.registerStateGauges();

        assertThat(meterRegistry.get("pingbell.notification.retry.pending.current").gauge().value()).isEqualTo(5.0);
    }

    @Test
    void exposesCurrentRetryDueNotificationCountAsAGaugeThatIsRecomputedOnEachRead() {
        when(notificationHistoryRepository.countRetryDueHistories(any())).thenReturn(4L);

        PingbellMetrics metrics = new PingbellMetrics(meterRegistry, incidentRepository, notificationHistoryRepository);
        metrics.registerStateGauges();

        assertThat(meterRegistry.get("pingbell.notification.retry.due.current").gauge().value()).isEqualTo(4.0);

        when(notificationHistoryRepository.countRetryDueHistories(any())).thenReturn(0L);
        assertThat(meterRegistry.get("pingbell.notification.retry.due.current").gauge().value()).isEqualTo(0.0);
    }

    @Test
    void recordsNotificationRetryExhaustedAsATaggedCounter() {
        PingbellMetrics metrics = new PingbellMetrics(meterRegistry, incidentRepository, notificationHistoryRepository);
        metrics.registerStateGauges();

        metrics.recordNotificationRetryExhausted(NotificationChannelType.SLACK, NotificationType.INCIDENT_OPEN);
        metrics.recordNotificationRetryExhausted(NotificationChannelType.SLACK, NotificationType.INCIDENT_OPEN);

        assertThat(
                meterRegistry.get("pingbell.notification.retry.exhausted.total")
                        .tag("channel_type", "SLACK")
                        .tag("notification_type", "INCIDENT_OPEN")
                        .counter()
                        .count()
        ).isEqualTo(2.0);
    }
}
