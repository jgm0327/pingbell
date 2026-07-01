package com.monit.pingbell.global.dlq;

public record DlqReprocessResult(
        String sourceTopic,
        String payloadType,
        String messageKey,
        DlqDryRunStatus dryRunStatus,
        String reason
) {
}
