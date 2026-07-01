package com.monit.pingbell.global.dlq;

import com.monit.pingbell.check.scheduler.event.HealthCheckRequestedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DlqBatchDryRunServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 1, 10, 0);

    @Mock
    private DlqDryRunService dryRunService;

    @Test
    void verifySummarizesDryRunStatuses() {
        DlqBatchDryRunService service = new DlqBatchDryRunService(dryRunService);
        HealthCheckRequestedEvent reprocessableEvent = requestedEvent(10L);
        HealthCheckRequestedEvent alreadyProcessedEvent = requestedEvent(11L);

        when(dryRunService.verify(reprocessableEvent)).thenReturn(DlqDryRunResult.reprocessable("retry"));
        when(dryRunService.verify(alreadyProcessedEvent)).thenReturn(DlqDryRunResult.alreadyProcessed("already done"));

        DlqBatchDryRunSummary summary = service.verify(List.of(
                readableRecord(0, reprocessableEvent),
                readableRecord(1, alreadyProcessedEvent),
                DlqRecordSnapshot.unreadable(
                        "pingbell.health-check.requested.dlq",
                        0,
                        2,
                        "INVALID_PAYLOAD",
                        "Invalid DLQ payload schema"
                )
        ));

        assertThat(summary.records()).hasSize(3);
        assertThat(summary.reprocessableCount()).isEqualTo(1);
        assertThat(summary.skipAlreadyProcessedCount()).isEqualTo(1);
        assertThat(summary.notReprocessableCount()).isEqualTo(1);
        assertThat(summary.records().get(2).reason()).contains("Invalid DLQ payload schema");
    }

    @Test
    void verifyDoesNotCallDryRunServiceForUnreadableRecord() {
        DlqBatchDryRunService service = new DlqBatchDryRunService(dryRunService);
        DlqRecordSnapshot unreadableRecord = DlqRecordSnapshot.unreadable(
                "pingbell.health-check.requested.dlq",
                0,
                0,
                "TOMBSTONE",
                "DLQ record value is null"
        );

        DlqBatchDryRunSummary summary = service.verify(List.of(unreadableRecord));

        assertThat(summary.notReprocessableCount()).isEqualTo(1);
        assertThat(summary.records().getFirst().status()).isEqualTo(DlqDryRunStatus.NOT_REPROCESSABLE);
        verify(dryRunService, never()).verify(org.mockito.ArgumentMatchers.any());
    }

    private DlqRecordSnapshot readableRecord(long offset, HealthCheckRequestedEvent event) {
        return DlqRecordSnapshot.readable(
                "pingbell.health-check.requested.dlq",
                0,
                offset,
                "HealthCheckRequestedEvent",
                "monitorId=" + event.monitorId(),
                event
        );
    }

    private HealthCheckRequestedEvent requestedEvent(Long monitorId) {
        return new HealthCheckRequestedEvent(
                UUID.randomUUID(),
                NOW,
                monitorId,
                20L,
                1000,
                30,
                NOW,
                "SCHEDULER"
        );
    }
}
