package com.monit.pingbell.global.dlq;

import com.monit.pingbell.check.domain.CheckResult;
import com.monit.pingbell.check.domain.CheckStatus;
import com.monit.pingbell.check.repository.CheckResultRepository;
import com.monit.pingbell.check.scheduler.event.HealthCheckCompletedEvent;
import com.monit.pingbell.check.scheduler.event.HealthCheckRequestedEvent;
import com.monit.pingbell.incident.domain.Incident;
import com.monit.pingbell.incident.domain.IncidentStatus;
import com.monit.pingbell.incident.repository.IncidentDetectionProcessedCheckResultRepository;
import com.monit.pingbell.incident.repository.IncidentRepository;
import com.monit.pingbell.member.domain.Member;
import com.monit.pingbell.monitor.domain.Monitor;
import com.monit.pingbell.monitor.domain.MonitorStatus;
import com.monit.pingbell.monitor.repository.MonitorRepository;
import com.monit.pingbell.notification.event.NotificationRequestedEvent;
import com.monit.pingbell.notification.repository.NotificationHistoryRepository;
import com.monit.pingbell.notification.type.NotificationType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DlqDryRunServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 1, 10, 0);

    @Mock
    private MonitorRepository monitorRepository;

    @Mock
    private CheckResultRepository checkResultRepository;

    @Mock
    private IncidentRepository incidentRepository;

    @Mock
    private IncidentDetectionProcessedCheckResultRepository processedCheckResultRepository;

    @Mock
    private NotificationHistoryRepository notificationHistoryRepository;

    @InjectMocks
    private DlqDryRunService dryRunService;

    @Test
    void healthCheckRequestedIsReprocessableWhenMonitorMatchesAndIsDue() {
        Monitor monitor = monitor(MonitorStatus.ACTIVE, NOW);
        HealthCheckRequestedEvent event = requestedEvent(NOW);

        when(monitorRepository.findById(10L)).thenReturn(Optional.of(monitor));

        DlqDryRunResult result = dryRunService.verify(event);

        assertThat(result.status()).isEqualTo(DlqDryRunStatus.REPROCESSABLE);
    }

    @Test
    void healthCheckRequestedSkipsWhenMonitorWasAlreadyProcessed() {
        Monitor monitor = monitor(MonitorStatus.ACTIVE, NOW.plusSeconds(30));
        HealthCheckRequestedEvent event = requestedEvent(NOW);

        when(monitorRepository.findById(10L)).thenReturn(Optional.of(monitor));

        DlqDryRunResult result = dryRunService.verify(event);

        assertThat(result.status()).isEqualTo(DlqDryRunStatus.SKIP_ALREADY_PROCESSED);
        assertThat(result.reason()).contains("nextCheckAt");
    }

    @Test
    void healthCheckRequestedIsNotReprocessableWhenMonitorIsPaused() {
        Monitor monitor = monitor(MonitorStatus.PAUSED, NOW);
        HealthCheckRequestedEvent event = requestedEvent(NOW);

        when(monitorRepository.findById(10L)).thenReturn(Optional.of(monitor));

        DlqDryRunResult result = dryRunService.verify(event);

        assertThat(result.status()).isEqualTo(DlqDryRunStatus.NOT_REPROCESSABLE);
        assertThat(result.reason()).contains("paused");
    }

    @Test
    void healthCheckCompletedSkipsWhenCheckResultAlreadyProcessed() {
        Monitor monitor = monitor(MonitorStatus.ACTIVE, NOW);
        CheckResult checkResult = checkResult(monitor, 30L);
        HealthCheckCompletedEvent event = completedEvent();

        when(checkResultRepository.findById(30L)).thenReturn(Optional.of(checkResult));
        when(processedCheckResultRepository.existsByCheckResultId(30L)).thenReturn(true);

        DlqDryRunResult result = dryRunService.verify(event);

        assertThat(result.status()).isEqualTo(DlqDryRunStatus.SKIP_ALREADY_PROCESSED);
        assertThat(result.reason()).contains("already processed");
    }

    @Test
    void healthCheckCompletedIsNotReprocessableWhenCheckResultIsMissing() {
        HealthCheckCompletedEvent event = completedEvent();

        when(checkResultRepository.findById(30L)).thenReturn(Optional.empty());

        DlqDryRunResult result = dryRunService.verify(event);

        assertThat(result.status()).isEqualTo(DlqDryRunStatus.NOT_REPROCESSABLE);
        assertThat(result.reason()).contains("CheckResult not found");
    }

    @Test
    void healthCheckCompletedRejectsMemberMismatch() {
        Monitor monitor = monitor(MonitorStatus.ACTIVE, NOW);
        CheckResult checkResult = checkResult(monitor, 30L);
        HealthCheckCompletedEvent event = new HealthCheckCompletedEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                10L,
                999L,
                30L,
                CheckStatus.SUCCESS,
                200,
                100L,
                null,
                NOW
        );

        when(checkResultRepository.findById(30L)).thenReturn(Optional.of(checkResult));

        DlqDryRunResult result = dryRunService.verify(event);

        assertThat(result.status()).isEqualTo(DlqDryRunStatus.NOT_REPROCESSABLE);
        assertThat(result.reason()).contains("memberId");
    }

    @Test
    void notificationRequestedIsReprocessableWhenIncidentMatchesAndNoHistoryExists() {
        Incident incident = incident(monitor(MonitorStatus.DOWN, NOW), 40L);
        NotificationRequestedEvent event = notificationEvent();

        when(incidentRepository.findById(40L)).thenReturn(Optional.of(incident));
        when(notificationHistoryRepository.existsByIncidentIdAndNotificationType(40L, NotificationType.INCIDENT_OPEN))
                .thenReturn(false);

        DlqDryRunResult result = dryRunService.verify(event);

        assertThat(result.status()).isEqualTo(DlqDryRunStatus.REPROCESSABLE);
    }

    @Test
    void notificationRequestedSkipsWhenHistoryAlreadyExists() {
        Incident incident = incident(monitor(MonitorStatus.DOWN, NOW), 40L);
        NotificationRequestedEvent event = notificationEvent();

        when(incidentRepository.findById(40L)).thenReturn(Optional.of(incident));
        when(notificationHistoryRepository.existsByIncidentIdAndNotificationType(40L, NotificationType.INCIDENT_OPEN))
                .thenReturn(true);

        DlqDryRunResult result = dryRunService.verify(event);

        assertThat(result.status()).isEqualTo(DlqDryRunStatus.SKIP_ALREADY_PROCESSED);
        assertThat(result.reason()).contains("history");
    }

    private HealthCheckRequestedEvent requestedEvent(LocalDateTime scheduledAt) {
        return new HealthCheckRequestedEvent(
                UUID.randomUUID(),
                scheduledAt,
                10L,
                20L,
                1000,
                30,
                scheduledAt,
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
                CheckStatus.SUCCESS,
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

    private CheckResult checkResult(Monitor monitor, Long id) {
        CheckResult checkResult = CheckResult.builder()
                .monitor(monitor)
                .status(CheckStatus.SUCCESS)
                .httpStatus(200)
                .responseTimeMs(100L)
                .build();
        ReflectionTestUtils.setField(checkResult, "id", id);
        return checkResult;
    }

    private Incident incident(Monitor monitor, Long id) {
        Incident incident = Incident.builder()
                .monitor(monitor)
                .status(IncidentStatus.OPEN)
                .startedAt(NOW)
                .lastErrorMessage("HTTP 500")
                .build();
        ReflectionTestUtils.setField(incident, "id", id);
        return incident;
    }

    private Monitor monitor(MonitorStatus status, LocalDateTime nextCheckAt) {
        Member member = Member.builder()
                .email("user@example.com")
                .password("password")
                .build();
        ReflectionTestUtils.setField(member, "id", 20L);

        Monitor monitor = Monitor.builder()
                .member(member)
                .name("api")
                .url("http://localhost:9999/health")
                .intervalSeconds(30)
                .timeoutMillis(1000)
                .failureThreshold(3)
                .recoveryThreshold(2)
                .status(status)
                .nextCheckAt(nextCheckAt)
                .build();
        ReflectionTestUtils.setField(monitor, "id", 10L);
        return monitor;
    }
}
