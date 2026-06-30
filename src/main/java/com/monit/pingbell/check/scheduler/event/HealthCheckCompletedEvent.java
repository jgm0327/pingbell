package com.monit.pingbell.check.scheduler.event;

import com.monit.pingbell.check.domain.CheckResult;
import com.monit.pingbell.check.domain.CheckStatus;
import com.monit.pingbell.monitor.domain.Monitor;

import java.time.LocalDateTime;
import java.util.UUID;

public record HealthCheckCompletedEvent(
        UUID eventId,
        UUID requestEventId,
        Long monitorId,
        Long memberId,
        Long checkResultId,
        CheckStatus status,
        Integer httpStatus,
        Long responseTimeMs,
        String errorMessage,
        LocalDateTime checkedAt
) {
    public static HealthCheckCompletedEvent from(
            HealthCheckRequestedEvent requestEvent,
            CheckResult checkResult,
            LocalDateTime checkedAt
    ) {
        Monitor monitor = checkResult.getMonitor();
        return new HealthCheckCompletedEvent(
                UUID.randomUUID(),
                requestEvent.eventId(),
                monitor.getId(),
                monitor.getMember().getId(),
                checkResult.getId(),
                checkResult.getStatus(),
                checkResult.getHttpStatus(),
                checkResult.getResponseTimeMs(),
                checkResult.getErrorMessage(),
                checkedAt
        );
    }
}
