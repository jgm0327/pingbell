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
@ConditionalOnProperty(name = "pingbell.dlq.reprocess.enabled", havingValue = "true")
public class DlqReprocessCommandRunner implements ApplicationRunner {

    private final ApplicationContext applicationContext;
    private final DlqRecordReader recordReader;
    private final DlqReprocessService reprocessService;

    @Value("${pingbell.dlq.reprocess.topic}")
    private String topic;

    @Value("${pingbell.dlq.reprocess.partition:0}")
    private int partition;

    @Value("${pingbell.dlq.reprocess.offset}")
    private long offset;

    @Value("${confirm-reprocess:false}")
    private boolean confirmReprocess;

    @Override
    public void run(ApplicationArguments args) {
        try {
            Object event = recordReader.read(topic, partition, offset);
            DlqReprocessResult result = reprocessService.reprocess(topic, event, confirmReprocess);
            System.out.printf(
                    "DLQ_REPROCESS topic=%s partition=%d offset=%d sourceTopic=%s payloadType=%s messageKey=%s status=%s reason=%s%n",
                    topic,
                    partition,
                    offset,
                    result.sourceTopic(),
                    result.payloadType(),
                    result.messageKey(),
                    result.dryRunStatus(),
                    DlqReasonRedactor.redact(result.reason())
            );
            SpringApplication.exit(applicationContext, () -> 0);
        } catch (RuntimeException exception) {
            System.out.printf(
                    "DLQ_REPROCESS topic=%s partition=%d offset=%d status=FAILED reason=%s%n",
                    topic,
                    partition,
                    offset,
                    DlqReasonRedactor.redact(exception.getMessage())
            );
            SpringApplication.exit(applicationContext, () -> 1);
        }
    }
}
