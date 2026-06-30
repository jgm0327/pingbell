package com.monit.pingbell.check.dto;

public record MonitorCheckTrendRawPoint(
        Integer year,
        Integer month,
        Integer day,
        Integer hour,
        Long successCount,
        Long failureCount,
        Double averageResponseTimeMs
) {
}
