package com.monit.pingbell.check.dto;

import com.monit.pingbell.check.domain.CheckStatus;

public record MonitorFailureSummaryRawPoint(
        CheckStatus status,
        Long count
) {
}
