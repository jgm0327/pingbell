package com.monit.pingbell.global.dlq;

public record DlqBatchDryRunRecordResult(
        DlqRecordSnapshot record,
        DlqDryRunStatus status,
        String reason
) {
}
