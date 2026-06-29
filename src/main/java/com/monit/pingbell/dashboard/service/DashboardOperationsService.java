package com.monit.pingbell.dashboard.service;

import com.monit.pingbell.check.domain.CheckStatus;
import com.monit.pingbell.check.repository.CheckResultRepository;
import com.monit.pingbell.dashboard.dto.OperationsSummaryResponse;
import com.monit.pingbell.incident.domain.IncidentStatus;
import com.monit.pingbell.incident.repository.IncidentRepository;
import com.monit.pingbell.notification.repository.NotificationHistoryRepository;
import com.monit.pingbell.notification.type.NotificationStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DashboardOperationsService {

    private static final int DEFAULT_WINDOW_HOURS = 24;
    private static final List<CheckStatus> FAILURE_STATUSES = List.of(
            CheckStatus.FAILURE,
            CheckStatus.TIMEOUT,
            CheckStatus.HTTP_ERROR,
            CheckStatus.SLOW_RESPONSE
    );

    private final CheckResultRepository checkResultRepository;
    private final IncidentRepository incidentRepository;
    private final NotificationHistoryRepository notificationHistoryRepository;

    @Transactional(readOnly = true)
    public OperationsSummaryResponse getOperationsSummary(Long memberId, LocalDateTime now) {
        LocalDateTime since = now.minusHours(DEFAULT_WINDOW_HOURS);

        long healthCheckSuccessCount = checkResultRepository
                .countByMonitorMemberIdAndStatusAndCreatedAtGreaterThanEqual(memberId, CheckStatus.SUCCESS, since);
        long healthCheckFailureCount = checkResultRepository
                .countByMonitorMemberIdAndStatusInAndCreatedAtGreaterThanEqual(memberId, FAILURE_STATUSES, since);
        long healthCheckTotalCount = healthCheckSuccessCount + healthCheckFailureCount;

        long openIncidentCount = incidentRepository.countByMonitorMemberIdAndStatus(memberId, IncidentStatus.OPEN);
        long openedIncidentCount = incidentRepository.countByMonitorMemberIdAndStartedAtGreaterThanEqual(memberId, since);
        long resolvedIncidentCount = incidentRepository.countByMonitorMemberIdAndResolvedAtGreaterThanEqual(memberId, since);

        long sentNotificationCount = notificationHistoryRepository
                .countByChannelMemberIdAndStatusAndCreatedAtGreaterThanEqual(memberId, NotificationStatus.SENT, since);
        long failedNotificationCount = notificationHistoryRepository
                .countByChannelMemberIdAndStatusAndCreatedAtGreaterThanEqual(memberId, NotificationStatus.FAILED, since);
        long retryPendingNotificationCount = notificationHistoryRepository
                .countByChannelMemberIdAndStatusAndCreatedAtGreaterThanEqual(memberId, NotificationStatus.RETRY_PENDING, since);

        return new OperationsSummaryResponse(
                DEFAULT_WINDOW_HOURS,
                since,
                new OperationsSummaryResponse.HealthCheckSummary(
                        healthCheckSuccessCount,
                        healthCheckFailureCount,
                        healthCheckTotalCount,
                        successRate(healthCheckSuccessCount, healthCheckTotalCount)
                ),
                new OperationsSummaryResponse.IncidentSummary(
                        openIncidentCount,
                        openedIncidentCount,
                        resolvedIncidentCount
                ),
                new OperationsSummaryResponse.NotificationSummary(
                        sentNotificationCount,
                        failedNotificationCount,
                        retryPendingNotificationCount
                )
        );
    }

    private int successRate(long successCount, long totalCount) {
        if (totalCount == 0) {
            return 0;
        }

        return (int) Math.round((successCount * 100.0) / totalCount);
    }
}
