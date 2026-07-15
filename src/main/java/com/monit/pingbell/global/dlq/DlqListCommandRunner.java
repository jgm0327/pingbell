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
@ConditionalOnProperty(name = "pingbell.dlq.list.enabled", havingValue = "true")
public class DlqListCommandRunner implements ApplicationRunner {

    private final ApplicationContext applicationContext;
    private final DlqRecordReader recordReader;

    @Value("${pingbell.dlq.list.topic}")
    private String topic;

    @Value("${pingbell.dlq.list.max-records:10}")
    private int maxRecords;

    @Override
    public void run(ApplicationArguments args) {
        try {
            var records = recordReader.readList(topic, maxRecords);
            for (DlqRecordSnapshot record : records) {
                System.out.printf(
                        "DLQ_RECORD topic=%s partition=%d offset=%d payloadType=%s primaryIds=\"%s\" readStatus=%s reason=%s%n",
                        record.topic(),
                        record.partition(),
                        record.offset(),
                        record.payloadType(),
                        record.primaryIds(),
                        record.isReadable() ? "READABLE" : "UNREADABLE",
                        DlqReasonRedactor.redact(record.readError())
                );
            }
            System.out.printf("DLQ_LIST_SUMMARY topic=%s requestedMax=%d returned=%d%n", topic, maxRecords, records.size());
            SpringApplication.exit(applicationContext, () -> 0);
        } catch (RuntimeException exception) {
            System.out.printf(
                    "DLQ_LIST_SUMMARY topic=%s requestedMax=%d status=FAILED reason=%s%n",
                    topic,
                    maxRecords,
                    DlqReasonRedactor.redact(exception.getMessage())
            );
            SpringApplication.exit(applicationContext, () -> 1);
        }
    }
}
