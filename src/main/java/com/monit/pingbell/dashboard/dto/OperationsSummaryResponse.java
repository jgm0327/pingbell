package com.monit.pingbell.dashboard.dto;

import java.time.LocalDateTime;
import java.util.List;

public record OperationsSummaryResponse(
        int windowHours,
        LocalDateTime since,
        HealthCheckSummary healthCheck,
        IncidentSummary incident,
        NotificationSummary notification,
        List<HealthCheckTrendPoint> healthCheckTrend
) {

    public record HealthCheckSummary(
            long successCount,
            long failureCount,
            long totalCount,
            int successRate
    ) {
    }

    public record IncidentSummary(
            long openCurrent,
            long openedCount,
            long resolvedCount
    ) {
    }

    public record NotificationSummary(
            long sentCount,
            long failedCount,
            long retryPendingCount,
            FailureTypeSummary failureTypes
    ) {
    }

    public record FailureTypeSummary(
            long channelDisabledCount,
            long sendFailedCount,
            long retryExhaustedCount
    ) {
    }

    public record HealthCheckTrendPoint(
            LocalDateTime bucketStart,
            long successCount,
            long failureCount,
            long totalCount,
            long averageResponseTimeMs
    ) {
    }
}
