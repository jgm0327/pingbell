package com.monit.pingbell.global.dlq;

import com.monit.pingbell.check.scheduler.event.HealthCheckCompletedEvent;
import com.monit.pingbell.check.scheduler.event.HealthCheckRequestedEvent;
import com.monit.pingbell.notification.event.NotificationRequestedEvent;
import com.monit.pingbell.notification.type.NotificationType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.monit.pingbell.check.domain.CheckStatus.SUCCESS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DlqReprocessServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 1, 10, 0);

    @Mock
    private DlqDryRunService dryRunService;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Test
    void reprocessRequiresConfirmOption() {
        DlqReprocessService service = new DlqReprocessService(dryRunService, kafkaTemplate);
        HealthCheckRequestedEvent event = requestedEvent();

        assertThatThrownBy(() -> service.reprocess("pingbell.health-check.requested.dlq", event, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("confirm-reprocess=true");

        verify(kafkaTemplate, never()).send(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void reprocessRejectsNonDlqTopic() {
        DlqReprocessService service = new DlqReprocessService(dryRunService, kafkaTemplate);
        HealthCheckRequestedEvent event = requestedEvent();

        assertThatThrownBy(() -> service.reprocess("pingbell.health-check.requested", event, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(".dlq");

        verify(kafkaTemplate, never()).send(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void reprocessRejectsNonReprocessableDryRunResult() {
        DlqReprocessService service = new DlqReprocessService(dryRunService, kafkaTemplate);
        HealthCheckRequestedEvent event = requestedEvent();

        when(dryRunService.verify(event)).thenReturn(DlqDryRunResult.alreadyProcessed("already processed"));

        assertThatThrownBy(() -> service.reprocess("pingbell.health-check.requested.dlq", event, true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SKIP_ALREADY_PROCESSED");

        verify(kafkaTemplate, never()).send(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void reprocessPublishesHealthCheckRequestedToSourceTopic() {
        DlqReprocessService service = new DlqReprocessService(dryRunService, kafkaTemplate);
        HealthCheckRequestedEvent event = requestedEvent();

        when(dryRunService.verify(event)).thenReturn(DlqDryRunResult.reprocessable("ok"));
        when(kafkaTemplate.send("pingbell.health-check.requested", "10", event))
                .thenReturn(CompletableFuture.completedFuture(null));

        DlqReprocessResult result = service.reprocess("pingbell.health-check.requested.dlq", event, true);

        assertThat(result.sourceTopic()).isEqualTo("pingbell.health-check.requested");
        assertThat(result.messageKey()).isEqualTo("10");
        verify(kafkaTemplate).send("pingbell.health-check.requested", "10", event);
    }

    @Test
    void reprocessPublishesHealthCheckCompletedWithMonitorIdKey() {
        DlqReprocessService service = new DlqReprocessService(dryRunService, kafkaTemplate);
        HealthCheckCompletedEvent event = completedEvent();

        when(dryRunService.verify(event)).thenReturn(DlqDryRunResult.reprocessable("ok"));
        when(kafkaTemplate.send("pingbell.health-check.completed", "10", event))
                .thenReturn(CompletableFuture.completedFuture(null));

        DlqReprocessResult result = service.reprocess("pingbell.health-check.completed.dlq", event, true);

        assertThat(result.sourceTopic()).isEqualTo("pingbell.health-check.completed");
        assertThat(result.messageKey()).isEqualTo("10");
        verify(kafkaTemplate).send("pingbell.health-check.completed", "10", event);
    }

    @Test
    void reprocessPublishesNotificationRequestedWithIncidentIdKey() {
        DlqReprocessService service = new DlqReprocessService(dryRunService, kafkaTemplate);
        NotificationRequestedEvent event = notificationEvent();

        when(dryRunService.verify(event)).thenReturn(DlqDryRunResult.reprocessable("ok"));
        when(kafkaTemplate.send("pingbell.notification.requested", "40", event))
                .thenReturn(CompletableFuture.completedFuture(null));

        DlqReprocessResult result = service.reprocess("pingbell.notification.requested.dlq", event, true);

        assertThat(result.sourceTopic()).isEqualTo("pingbell.notification.requested");
        assertThat(result.messageKey()).isEqualTo("40");
        verify(kafkaTemplate).send("pingbell.notification.requested", "40", event);
    }

    private HealthCheckRequestedEvent requestedEvent() {
        return new HealthCheckRequestedEvent(
                UUID.randomUUID(),
                NOW,
                10L,
                20L,
                1000,
                30,
                NOW,
                "SCHEDULER"
        );
    }

    private HealthCheckCompletedEvent completedEvent() {
        return new HealthCheckCompletedEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                10L,
                20L,
                30L,
                SUCCESS,
                200,
                100L,
                null,
                NOW
        );
    }

    private NotificationRequestedEvent notificationEvent() {
        return new NotificationRequestedEvent(
                UUID.randomUUID(),
                40L,
                10L,
                20L,
                NotificationType.INCIDENT_OPEN,
                NOW
        );
    }
}
