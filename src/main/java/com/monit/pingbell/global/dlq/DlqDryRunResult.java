package com.monit.pingbell.global.dlq;

public record DlqDryRunResult(
        DlqDryRunStatus status,
        String reason
) {
    public static DlqDryRunResult reprocessable(String reason) {
        return new DlqDryRunResult(DlqDryRunStatus.REPROCESSABLE, reason);
    }

    public static DlqDryRunResult alreadyProcessed(String reason) {
        return new DlqDryRunResult(DlqDryRunStatus.SKIP_ALREADY_PROCESSED, reason);
    }

    public static DlqDryRunResult notReprocessable(String reason) {
        return new DlqDryRunResult(DlqDryRunStatus.NOT_REPROCESSABLE, reason);
    }
}
