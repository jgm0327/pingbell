package com.monit.pingbell.global.dlq;

import com.monit.pingbell.check.scheduler.event.HealthCheckCompletedEvent;
import com.monit.pingbell.check.scheduler.event.HealthCheckRequestedEvent;
import com.monit.pingbell.notification.event.NotificationRequestedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DlqReprocessService {

    private static final String DLQ_SUFFIX = ".dlq";

    private final DlqDryRunService dryRunService;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public DlqReprocessResult reprocess(String dlqTopic, Object event, boolean confirmReprocess) {
        if (!confirmReprocess) {
            throw new IllegalArgumentException("confirm-reprocess=true is required for DLQ reprocess");
        }
        if (!dlqTopic.endsWith(DLQ_SUFFIX)) {
            throw new IllegalArgumentException("DLQ topic must end with .dlq. topic=" + dlqTopic);
        }

        DlqDryRunResult dryRunResult = dryRunService.verify(event);
        if (dryRunResult.status() != DlqDryRunStatus.REPROCESSABLE) {
            throw new IllegalStateException(
                    "DLQ record is not reprocessable. status=%s, reason=%s"
                            .formatted(dryRunResult.status(), dryRunResult.reason())
            );
        }

        String sourceTopic = sourceTopic(dlqTopic);
        String messageKey = messageKey(event);
        kafkaTemplate.send(sourceTopic, messageKey, event).join();

        return new DlqReprocessResult(
                sourceTopic,
                event.getClass().getSimpleName(),
                messageKey,
                dryRunResult.status(),
                dryRunResult.reason()
        );
    }

    private String sourceTopic(String dlqTopic) {
        return dlqTopic.substring(0, dlqTopic.length() - DLQ_SUFFIX.length());
    }

    private String messageKey(Object event) {
        if (event instanceof HealthCheckRequestedEvent requestedEvent) {
            return String.valueOf(requestedEvent.monitorId());
        }
        if (event instanceof HealthCheckCompletedEvent completedEvent) {
            return String.valueOf(completedEvent.monitorId());
        }
        if (event instanceof NotificationRequestedEvent notificationEvent) {
            return String.valueOf(notificationEvent.incidentId());
        }
        throw new IllegalArgumentException("Unsupported DLQ payload type: " + event.getClass().getName());
    }
}
