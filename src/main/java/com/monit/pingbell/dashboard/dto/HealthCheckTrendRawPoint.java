package com.monit.pingbell.dashboard.dto;

public record HealthCheckTrendRawPoint(
        Integer year,
        Integer month,
        Integer day,
        Integer hour,
        Long successCount,
        Long failureCount,
        Double averageResponseTimeMs
) {
}
