package com.monit.pingbell.dashboard.service;

import com.monit.pingbell.check.domain.CheckStatus;
import com.monit.pingbell.check.repository.CheckResultRepository;
import com.monit.pingbell.dashboard.dto.HealthCheckTrendRawPoint;
import com.monit.pingbell.incident.domain.IncidentStatus;
import com.monit.pingbell.incident.repository.IncidentRepository;
import com.monit.pingbell.notification.repository.NotificationHistoryRepository;
import com.monit.pingbell.notification.type.NotificationStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardOperationsServiceTest {

    @Mock
    private CheckResultRepository checkResultRepository;

    @Mock
    private IncidentRepository incidentRepository;

    @Mock
    private NotificationHistoryRepository notificationHistoryRepository;

    @InjectMocks
    private DashboardOperationsService dashboardOperationsService;

    @Test
    void getOperationsSummaryAggregatesRecentOperationalCounts() {
        Long memberId = 1L;
        LocalDateTime now = LocalDateTime.of(2026, 6, 29, 12, 0);
        LocalDateTime since = now.minusHours(24);

        when(checkResultRepository.countByMonitorMemberIdAndStatusAndCreatedAtGreaterThanEqual(memberId, CheckStatus.SUCCESS, since))
                .thenReturn(8L);
        when(checkResultRepository.countByMonitorMemberIdAndStatusInAndCreatedAtGreaterThanEqual(
                memberId,
                List.of(CheckStatus.FAILURE, CheckStatus.TIMEOUT, CheckStatus.HTTP_ERROR, CheckStatus.SLOW_RESPONSE),
                since
        )).thenReturn(2L);
        when(incidentRepository.countByMonitorMemberIdAndStatus(memberId, IncidentStatus.OPEN)).thenReturn(1L);
        when(incidentRepository.countByMonitorMemberIdAndStartedAtGreaterThanEqual(memberId, since)).thenReturn(3L);
        when(incidentRepository.countByMonitorMemberIdAndResolvedAtGreaterThanEqual(memberId, since)).thenReturn(2L);
        when(notificationHistoryRepository.countByChannelMemberIdAndStatusAndCreatedAtGreaterThanEqual(memberId, NotificationStatus.SENT, since))
                .thenReturn(5L);
        when(notificationHistoryRepository.countByChannelMemberIdAndStatusAndCreatedAtGreaterThanEqual(memberId, NotificationStatus.FAILED, since))
                .thenReturn(1L);
        when(notificationHistoryRepository.countRetryScheduledByChannelMemberIdAndCreatedAtGreaterThanEqual(memberId, since))
                .thenReturn(2L);
        when(notificationHistoryRepository.countChannelDisabledFailuresByChannelMemberIdAndCreatedAtGreaterThanEqual(memberId, since))
                .thenReturn(3L);
        when(notificationHistoryRepository.countSendFailedFailuresByChannelMemberIdAndCreatedAtGreaterThanEqual(memberId, since))
                .thenReturn(4L);
        when(notificationHistoryRepository.countRetryExhaustedFailuresByChannelMemberIdAndCreatedAtGreaterThanEqual(memberId, since))
                .thenReturn(5L);
        when(checkResultRepository.findHourlyTrendByMemberId(memberId, LocalDateTime.of(2026, 6, 28, 13, 0), now.plusNanos(1)))
                .thenReturn(List.of(
                        new HealthCheckTrendRawPoint(2026, 6, 28, 13, 2L, 1L, 120.4),
                        new HealthCheckTrendRawPoint(2026, 6, 29, 12, 3L, 0L, 80.0)
                ));

        var response = dashboardOperationsService.getOperationsSummary(memberId, now);

        assertThat(response.windowHours()).isEqualTo(24);
        assertThat(response.since()).isEqualTo(since);
        assertThat(response.healthCheck().successCount()).isEqualTo(8L);
        assertThat(response.healthCheck().failureCount()).isEqualTo(2L);
        assertThat(response.healthCheck().totalCount()).isEqualTo(10L);
        assertThat(response.healthCheck().successRate()).isEqualTo(80);
        assertThat(response.incident().openCurrent()).isEqualTo(1L);
        assertThat(response.incident().openedCount()).isEqualTo(3L);
        assertThat(response.incident().resolvedCount()).isEqualTo(2L);
        assertThat(response.notification().sentCount()).isEqualTo(5L);
        assertThat(response.notification().failedCount()).isEqualTo(1L);
        assertThat(response.notification().retryPendingCount()).isEqualTo(2L);
        assertThat(response.notification().failureTypes().channelDisabledCount()).isEqualTo(3L);
        assertThat(response.notification().failureTypes().sendFailedCount()).isEqualTo(4L);
        assertThat(response.notification().failureTypes().retryExhaustedCount()).isEqualTo(5L);
        assertThat(response.healthCheckTrend()).hasSize(24);
        assertThat(response.healthCheckTrend().getFirst().bucketStart()).isEqualTo(LocalDateTime.of(2026, 6, 28, 13, 0));
        assertThat(response.healthCheckTrend().getFirst().successCount()).isEqualTo(2L);
        assertThat(response.healthCheckTrend().getFirst().failureCount()).isEqualTo(1L);
        assertThat(response.healthCheckTrend().getFirst().totalCount()).isEqualTo(3L);
        assertThat(response.healthCheckTrend().getFirst().averageResponseTimeMs()).isEqualTo(120L);
        assertThat(response.healthCheckTrend().get(1).totalCount()).isZero();
        assertThat(response.healthCheckTrend().getLast().bucketStart()).isEqualTo(LocalDateTime.of(2026, 6, 29, 12, 0));
        assertThat(response.healthCheckTrend().getLast().averageResponseTimeMs()).isEqualTo(80L);

        verify(checkResultRepository).countByMonitorMemberIdAndStatusAndCreatedAtGreaterThanEqual(memberId, CheckStatus.SUCCESS, since);
    }
}
