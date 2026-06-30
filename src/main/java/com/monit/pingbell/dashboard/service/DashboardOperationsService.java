package com.monit.pingbell.dashboard.service;

import com.monit.pingbell.check.domain.CheckStatus;
import com.monit.pingbell.check.repository.CheckResultRepository;
import com.monit.pingbell.dashboard.dto.HealthCheckTrendRawPoint;
import com.monit.pingbell.dashboard.dto.OperationsSummaryResponse;
import com.monit.pingbell.incident.domain.IncidentStatus;
import com.monit.pingbell.incident.repository.IncidentRepository;
import com.monit.pingbell.notification.repository.NotificationHistoryRepository;
import com.monit.pingbell.notification.type.NotificationStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
public class DashboardOperationsService {

    private static final int DEFAULT_WINDOW_HOURS = 24;
    private static final int DEFAULT_TREND_BUCKET_COUNT = 24;
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
        List<OperationsSummaryResponse.HealthCheckTrendPoint> healthCheckTrend = getHourlyHealthCheckTrend(memberId, now);

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
                ),
                healthCheckTrend
        );
    }

    private List<OperationsSummaryResponse.HealthCheckTrendPoint> getHourlyHealthCheckTrend(Long memberId, LocalDateTime now) {
        LocalDateTime firstBucketStart = now.truncatedTo(ChronoUnit.HOURS).minusHours(DEFAULT_TREND_BUCKET_COUNT - 1L);
        LocalDateTime until = now.plusNanos(1);
        Map<LocalDateTime, HealthCheckTrendRawPoint> rawPoints = new HashMap<>();

        for (HealthCheckTrendRawPoint rawPoint : checkResultRepository.findHourlyTrendByMemberId(memberId, firstBucketStart, until)) {
            rawPoints.put(
                    LocalDateTime.of(rawPoint.year(), rawPoint.month(), rawPoint.day(), rawPoint.hour(), 0),
                    rawPoint
            );
        }

        return IntStream.range(0, DEFAULT_TREND_BUCKET_COUNT)
                .mapToObj(firstBucketStart::plusHours)
                .map(bucketStart -> toTrendPoint(bucketStart, rawPoints.get(bucketStart)))
                .toList();
    }

    private OperationsSummaryResponse.HealthCheckTrendPoint toTrendPoint(
            LocalDateTime bucketStart,
            HealthCheckTrendRawPoint rawPoint
    ) {
        if (rawPoint == null) {
            return new OperationsSummaryResponse.HealthCheckTrendPoint(bucketStart, 0, 0, 0, 0);
        }

        long successCount = rawPoint.successCount() == null ? 0 : rawPoint.successCount();
        long failureCount = rawPoint.failureCount() == null ? 0 : rawPoint.failureCount();
        long averageResponseTimeMs = rawPoint.averageResponseTimeMs() == null
                ? 0
                : Math.round(rawPoint.averageResponseTimeMs());

        return new OperationsSummaryResponse.HealthCheckTrendPoint(
                bucketStart,
                successCount,
                failureCount,
                successCount + failureCount,
                averageResponseTimeMs
        );
    }

    private int successRate(long successCount, long totalCount) {
        if (totalCount == 0) {
            return 0;
        }

        return (int) Math.round((successCount * 100.0) / totalCount);
    }
}
