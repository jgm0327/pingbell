package com.monit.pingbell.global.dlq;

import java.util.List;

public record DlqBatchDryRunSummary(
        List<DlqBatchDryRunRecordResult> records,
        long reprocessableCount,
        long skipAlreadyProcessedCount,
        long notReprocessableCount
) {
}
