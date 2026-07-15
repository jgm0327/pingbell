package com.monit.pingbell.check.dto;

import com.monit.pingbell.check.domain.CheckResult;
import com.monit.pingbell.check.domain.CheckStatus;

import java.time.LocalDateTime;

public record CheckResultResponse(
        Long id,
        Long monitorId,
        CheckStatus status,
        Integer httpStatus,
        Long responseTimeMs,
        String errorMessage,
        LocalDateTime checkedAt
) {
    public static CheckResultResponse from(CheckResult checkResult) {
        return new CheckResultResponse(
                checkResult.getId(),
                checkResult.getMonitor().getId(),
                checkResult.getStatus(),
                checkResult.getHttpStatus(),
                checkResult.getResponseTimeMs(),
                checkResult.getErrorMessage(),
                checkResult.getCreatedAt()
        );
    }
}
