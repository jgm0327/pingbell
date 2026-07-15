package com.monit.pingbell.incident.service;

import com.monit.pingbell.check.domain.CheckResult;
import com.monit.pingbell.check.domain.CheckStatus;
import com.monit.pingbell.check.repository.CheckResultRepository;
import com.monit.pingbell.check.scheduler.event.HealthCheckCompletedEvent;
import com.monit.pingbell.global.observability.PingbellMetrics;
import com.monit.pingbell.incident.domain.Incident;
import com.monit.pingbell.incident.domain.IncidentDetectionProcessedCheckResult;
import com.monit.pingbell.incident.domain.IncidentStatus;
import com.monit.pingbell.incident.repository.IncidentDetectionProcessedCheckResultRepository;
import com.monit.pingbell.incident.repository.IncidentRepository;
import com.monit.pingbell.member.domain.Member;
import com.monit.pingbell.monitor.domain.Monitor;
import com.monit.pingbell.monitor.domain.MonitorStatus;
import com.monit.pingbell.notification.service.NotificationDispatchService;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IncidentDetectionServiceTest {

    @Mock
    private CheckResultRepository checkResultRepository;

    @Mock
    private IncidentRepository incidentRepository;

    @Mock
    private IncidentDetectionProcessedCheckResultRepository processedRepository;

    @Mock
    private NotificationDispatchService notificationDispatchService;

    @Mock
    private PingbellMetrics metrics;

    @InjectMocks
    private IncidentDetectionService incidentDetectionService;

    @Test
    void detectFromCompletedEventLoadsCheckResultAndOpensIncident() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 1, 10, 0);
        Monitor monitor = monitor(MonitorStatus.ACTIVE, 1, 2);
        CheckResult checkResult = checkResult(monitor, CheckStatus.HTTP_ERROR, 500, null, 30L);
        HealthCheckCompletedEvent event = event(10L, 20L, 30L);

        when(checkResultRepository.findById(30L)).thenReturn(Optional.of(checkResult));
        when(processedRepository.existsByCheckResultId(30L)).thenReturn(false);
        when(processedRepository.saveAndFlush(any(IncidentDetectionProcessedCheckResult.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(incidentRepository.existsByMonitorAndStatus(monitor, IncidentStatus.OPEN)).thenReturn(false);
        when(incidentRepository.save(any(Incident.class))).thenAnswer(invocation -> invocation.getArgument(0));

        incidentDetectionService.detectFromCompletedEvent(event, now);

        var incidentCaptor = org.mockito.ArgumentCaptor.forClass(Incident.class);
        verify(incidentRepository).save(incidentCaptor.capture());
        assertThat(incidentCaptor.getValue().getStartedAt()).isEqualTo(now);
        assertThat(incidentCaptor.getValue().getLastErrorMessage()).isEqualTo("HTTP 500");
        assertThat(monitor.getStatus()).isEqualTo(MonitorStatus.DOWN);
        verify(notificationDispatchService).dispatch(incidentCaptor.getValue(), NotificationType.INCIDENT_OPEN, now);
    }

    @Test
    void detectFromCompletedEventSkipsDuplicateCheckResultId() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 1, 10, 0);
        Monitor monitor = monitor(MonitorStatus.ACTIVE, 1, 2);
        CheckResult checkResult = checkResult(monitor, CheckStatus.HTTP_ERROR, 500, null, 30L);
        HealthCheckCompletedEvent event = event(10L, 20L, 30L);

        when(checkResultRepository.findById(30L)).thenReturn(Optional.of(checkResult));
        when(processedRepository.existsByCheckResultId(30L)).thenReturn(true);

        incidentDetectionService.detectFromCompletedEvent(event, now);

        verify(processedRepository, never()).saveAndFlush(any());
        verify(incidentRepository, never()).save(any());
        assertThat(monitor.getStatus()).isEqualTo(MonitorStatus.ACTIVE);
    }

    @Test
    void detectFromCompletedEventRejectsPayloadThatDoesNotMatchCheckResultMonitor() {
        Monitor monitor = monitor(MonitorStatus.ACTIVE, 1, 2);
        CheckResult checkResult = checkResult(monitor, CheckStatus.SUCCESS, 200, null, 30L);
        HealthCheckCompletedEvent event = event(999L, 20L, 30L);

        when(checkResultRepository.findById(30L)).thenReturn(Optional.of(checkResult));

        assertThatThrownBy(() -> incidentDetectionService.detectFromCompletedEvent(event, LocalDateTime.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not match CheckResult");

        verify(processedRepository, never()).saveAndFlush(any());
    }

    @Test
    void detectResolvesOpenIncidentWhenRecoveryThresholdIsReached() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 1, 10, 0);
        Monitor monitor = monitor(MonitorStatus.DOWN, 1, 1);
        CheckResult checkResult = checkResult(monitor, CheckStatus.SUCCESS, 200, null, 30L);
        Incident incident = Incident.builder()
                .monitor(monitor)
                .status(IncidentStatus.OPEN)
                .startedAt(now.minusMinutes(5))
                .lastErrorMessage("HTTP 500")
                .build();

        when(incidentRepository.findByMonitorAndStatus(monitor, IncidentStatus.OPEN))
                .thenReturn(Optional.of(incident));

        incidentDetectionService.detect(checkResult, now);

        assertThat(incident.getStatus()).isEqualTo(IncidentStatus.RESOLVED);
        assertThat(incident.getResolvedAt()).isEqualTo(now);
        assertThat(monitor.getStatus()).isEqualTo(MonitorStatus.ACTIVE);
        verify(notificationDispatchService).dispatch(incident, NotificationType.INCIDENT_RESOLVED, now);
    }

    private HealthCheckCompletedEvent event(Long monitorId, Long memberId, Long checkResultId) {
        return new HealthCheckCompletedEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                monitorId,
                memberId,
                checkResultId,
                CheckStatus.SUCCESS,
                200,
                100L,
                null,
                LocalDateTime.of(2026, 7, 1, 9, 59)
        );
    }

    private CheckResult checkResult(
            Monitor monitor,
            CheckStatus status,
            Integer httpStatus,
            String errorMessage,
            Long id
    ) {
        CheckResult checkResult = CheckResult.builder()
                .monitor(monitor)
                .status(status)
                .httpStatus(httpStatus)
                .responseTimeMs(100L)
                .errorMessage(errorMessage)
                .build();
        ReflectionTestUtils.setField(checkResult, "id", id);
        return checkResult;
    }

    private Monitor monitor(MonitorStatus status, int failureThreshold, int recoveryThreshold) {
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
                .failureThreshold(failureThreshold)
                .recoveryThreshold(recoveryThreshold)
                .status(status)
                .nextCheckAt(LocalDateTime.of(2026, 7, 1, 9, 59))
                .build();
        ReflectionTestUtils.setField(monitor, "id", 10L);
        return monitor;
    }
}
