package com.monit.pingbell.loganalysis.dto;

public record SuspectedCauseResponse(
        String title,
        Confidence confidence,
        String reason
) {
}
