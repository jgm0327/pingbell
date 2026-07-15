package com.monit.pingbell.monitor.dto;

import com.monit.pingbell.monitor.domain.Monitor;
import com.monit.pingbell.monitor.domain.MonitorStatus;

import java.time.LocalDateTime;

public record MonitorResponse(
        Long id,
        Long userId,
        String name,
        String url,
        Integer intervalSeconds,
        Integer timeoutMillis,
        Integer failureThreshold,
        Integer recoveryThreshold,
        MonitorStatus status,
        LocalDateTime nextCheckAt
) {
    public static MonitorResponse from(Monitor monitor) {
        return new MonitorResponse(
                monitor.getId(),
                monitor.getMember().getId(),
                monitor.getName(),
                monitor.getUrl(),
                monitor.getIntervalSeconds(),
                monitor.getTimeoutMillis(),
                monitor.getFailureThreshold(),
                monitor.getRecoveryThreshold(),
                monitor.getStatus(),
                monitor.getNextCheckAt()
        );
    }
}
