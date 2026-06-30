package com.monit.pingbell.check.dto;

import com.monit.pingbell.check.domain.CheckStatus;

import java.time.LocalDateTime;
import java.util.List;

public record MonitorCheckSummaryResponse(
        int windowHours,
        LocalDateTime since,
        List<TrendPoint> trend,
        List<FailureSummary> failureSummary
) {

    public record TrendPoint(
            LocalDateTime bucketStart,
            long successCount,
            long failureCount,
            long totalCount,
            long averageResponseTimeMs
    ) {
    }

    public record FailureSummary(
            CheckStatus status,
            long count
    ) {
    }
}
