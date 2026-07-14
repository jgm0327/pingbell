package com.monit.pingbell.loganalysis.dto;

public record RecommendedActionResponse(
        int priority,
        String action,
        String command
) {
}
