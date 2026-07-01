package com.monit.pingbell.global.dlq;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DlqBatchDryRunService {

    private final DlqDryRunService dryRunService;

    public DlqBatchDryRunSummary verify(List<DlqRecordSnapshot> records) {
        List<DlqBatchDryRunRecordResult> results = records.stream()
                .map(this::verifyRecord)
                .toList();

        return new DlqBatchDryRunSummary(
                results,
                count(results, DlqDryRunStatus.REPROCESSABLE),
                count(results, DlqDryRunStatus.SKIP_ALREADY_PROCESSED),
                count(results, DlqDryRunStatus.NOT_REPROCESSABLE)
        );
    }

    private DlqBatchDryRunRecordResult verifyRecord(DlqRecordSnapshot record) {
        if (!record.isReadable()) {
            return new DlqBatchDryRunRecordResult(
                    record,
                    DlqDryRunStatus.NOT_REPROCESSABLE,
                    record.readError()
            );
        }
        DlqDryRunResult result = dryRunService.verify(record.payload());
        return new DlqBatchDryRunRecordResult(record, result.status(), result.reason());
    }

    private long count(List<DlqBatchDryRunRecordResult> results, DlqDryRunStatus status) {
        return results.stream()
                .filter(result -> result.status() == status)
                .count();
    }
}
