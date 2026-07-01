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
@ConditionalOnProperty(name = "pingbell.dlq.dry-run.enabled", havingValue = "true")
public class DlqDryRunCommandRunner implements ApplicationRunner {

    private final ApplicationContext applicationContext;
    private final DlqRecordReader recordReader;
    private final DlqDryRunService dryRunService;

    @Value("${pingbell.dlq.dry-run.topic}")
    private String topic;

    @Value("${pingbell.dlq.dry-run.partition:0}")
    private int partition;

    @Value("${pingbell.dlq.dry-run.offset}")
    private long offset;

    @Override
    public void run(ApplicationArguments args) {
        try {
            Object event = recordReader.read(topic, partition, offset);
            DlqDryRunResult result = dryRunService.verify(event);
            System.out.printf(
                    "DLQ_DRY_RUN topic=%s partition=%d offset=%d payloadType=%s status=%s reason=%s%n",
                    topic,
                    partition,
                    offset,
                    event.getClass().getSimpleName(),
                    result.status(),
                    result.reason()
            );
            SpringApplication.exit(applicationContext, () -> 0);
        } catch (RuntimeException exception) {
            System.out.printf(
                    "DLQ_DRY_RUN topic=%s partition=%d offset=%d status=%s reason=%s%n",
                    topic,
                    partition,
                    offset,
                    DlqDryRunStatus.NOT_REPROCESSABLE,
                    exception.getMessage()
            );
            SpringApplication.exit(applicationContext, () -> 1);
        }
    }
}
