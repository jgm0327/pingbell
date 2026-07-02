package com.monit.pingbell.global.dlq;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "pingbell.dlq.batch-dry-run.enabled", havingValue = "true")
public class DlqBatchDryRunCommandRunner implements ApplicationRunner {

    private final ApplicationContext applicationContext;
    private final DlqRecordReader recordReader;
    private final DlqBatchDryRunService batchDryRunService;

    @Value("${pingbell.dlq.batch-dry-run.topic}")
    private String topic;

    @Value("${pingbell.dlq.batch-dry-run.max-records:10}")
    private int maxRecords;

    @Override
    public void run(ApplicationArguments args) {
        try {
            DlqBatchDryRunSummary summary = batchDryRunService.verify(recordReader.readList(topic, maxRecords));
            for (DlqBatchDryRunRecordResult result : summary.records()) {
                DlqRecordSnapshot record = result.record();
                System.out.printf(
                        "DLQ_BATCH_DRY_RUN topic=%s partition=%d offset=%d payloadType=%s primaryIds=\"%s\" status=%s reason=%s%n",
                        record.topic(),
                        record.partition(),
                        record.offset(),
                        record.payloadType(),
                        record.primaryIds(),
                        result.status(),
                        DlqReasonRedactor.redact(result.reason())
                );
            }
            System.out.printf(
                    "DLQ_BATCH_DRY_RUN_SUMMARY topic=%s requestedMax=%d returned=%d REPROCESSABLE=%d SKIP_ALREADY_PROCESSED=%d NOT_REPROCESSABLE=%d%n",
                    topic,
                    maxRecords,
                    summary.records().size(),
                    summary.reprocessableCount(),
                    summary.skipAlreadyProcessedCount(),
                    summary.notReprocessableCount()
            );
            SpringApplication.exit(applicationContext, () -> 0);
        } catch (RuntimeException exception) {
            System.out.printf(
                    "DLQ_BATCH_DRY_RUN_SUMMARY topic=%s requestedMax=%d status=FAILED reason=%s%n",
                    topic,
                    maxRecords,
                    DlqReasonRedactor.redact(exception.getMessage())
            );
            SpringApplication.exit(applicationContext, () -> 1);
        }
    }
}
