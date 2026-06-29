package com.monit.pingbell.dashboard.dto;

import java.time.LocalDateTime;

public record OperationsSummaryResponse(
        int windowHours,
        LocalDateTime since,
        HealthCheckSummary healthCheck,
        IncidentSummary incident,
        NotificationSummary notification
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
            long retryPendingCount
    ) {
    }
}
