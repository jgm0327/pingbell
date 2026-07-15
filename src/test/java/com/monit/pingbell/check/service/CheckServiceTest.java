package com.monit.pingbell.check.service;

import com.monit.pingbell.check.client.HealthCheckClient;
import com.monit.pingbell.check.domain.CheckResult;
import com.monit.pingbell.check.domain.CheckStatus;
import com.monit.pingbell.check.repository.CheckResultRepository;
import com.monit.pingbell.check.scheduler.event.HealthCheckRequestedEvent;
import com.monit.pingbell.global.observability.PingbellMetrics;
import com.monit.pingbell.incident.service.IncidentDetectionService;
import com.monit.pingbell.member.domain.Member;
import com.monit.pingbell.monitor.domain.Monitor;
import com.monit.pingbell.monitor.domain.MonitorStatus;
import com.monit.pingbell.monitor.repository.MonitorRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CheckServiceTest {

    @Mock
    private MonitorRepository monitorRepository;

    @Mock
    private HealthCheckClient healthCheckClient;

    @Mock
    private CheckResultRepository checkResultRepository;

    @Mock
    private IncidentDetectionService incidentDetectionService;

    @Mock
    private PingbellMetrics metrics;

    @InjectMocks
    private CheckService checkService;

    @Test
    void healthCheckSavesHttpErrorWhenResponseStatusIs4xxOr5xx() {
        LocalDateTime now = LocalDateTime.of(2026, 6, 27, 12, 0);
        Monitor monitor = monitor(3);

        when(monitorRepository.findAllByStatusInAndDeletedAtIsNullAndNextCheckAtLessThanEqual(
                eq(List.of(MonitorStatus.ACTIVE, MonitorStatus.DOWN)),
                any(LocalDateTime.class)
        )).thenReturn(List.of(monitor));
        when(healthCheckClient.check(monitor.getUrl(), monitor.getTimeoutMillis())).thenReturn(500);

        checkService.healthCheck(now);

        var checkResultCaptor = org.mockito.ArgumentCaptor.forClass(CheckResult.class);
        verify(checkResultRepository).save(checkResultCaptor.capture());
        assertThat(checkResultCaptor.getValue().getStatus()).isEqualTo(CheckStatus.HTTP_ERROR);
        assertThat(checkResultCaptor.getValue().getHttpStatus()).isEqualTo(500);
        verify(incidentDetectionService).detect(any(CheckResult.class), eq(now));
    }

    @Test
    void healthCheckSavesSlowResponseWhenSuccessfulResponseExceedsTimeoutMillis() {
        LocalDateTime now = LocalDateTime.of(2026, 6, 27, 12, 0);
        Monitor monitor = monitor(3, MonitorStatus.ACTIVE, 1);

        when(monitorRepository.findAllByStatusInAndDeletedAtIsNullAndNextCheckAtLessThanEqual(
                eq(List.of(MonitorStatus.ACTIVE, MonitorStatus.DOWN)),
                any(LocalDateTime.class)
        )).thenReturn(List.of(monitor));
        when(healthCheckClient.check(monitor.getUrl(), monitor.getTimeoutMillis())).thenAnswer(invocation -> {
            Thread.sleep(5);
            return 200;
        });

        checkService.healthCheck(now);

        var checkResultCaptor = org.mockito.ArgumentCaptor.forClass(CheckResult.class);
        verify(checkResultRepository).save(checkResultCaptor.capture());
        assertThat(checkResultCaptor.getValue().getStatus()).isEqualTo(CheckStatus.SLOW_RESPONSE);
        assertThat(checkResultCaptor.getValue().getHttpStatus()).isEqualTo(200);
        verify(incidentDetectionService).detect(any(CheckResult.class), eq(now));
    }

    @Test
    void healthCheckDelegatesIncidentDetectionAfterSavingResult() {
        LocalDateTime now = LocalDateTime.of(2026, 6, 19, 12, 0);
        Monitor monitor = monitor(2);

        when(monitorRepository.findAllByStatusInAndDeletedAtIsNullAndNextCheckAtLessThanEqual(
                eq(List.of(MonitorStatus.ACTIVE, MonitorStatus.DOWN)),
                any(LocalDateTime.class)
        )).thenReturn(List.of(monitor));
        when(healthCheckClient.check(monitor.getUrl(), monitor.getTimeoutMillis())).thenReturn(500);

        checkService.healthCheck(now);

        var checkResultCaptor = org.mockito.ArgumentCaptor.forClass(CheckResult.class);
        verify(checkResultRepository).save(checkResultCaptor.capture());
        verify(incidentDetectionService).detect(checkResultCaptor.getValue(), now);
    }

    @Test
    void healthCheckSavesExceptionNameWhenRequestFails() {
        LocalDateTime now = LocalDateTime.of(2026, 6, 19, 12, 0);
        Monitor monitor = monitor(1);

        when(monitorRepository.findAllByStatusInAndDeletedAtIsNullAndNextCheckAtLessThanEqual(
                eq(List.of(MonitorStatus.ACTIVE, MonitorStatus.DOWN)),
                any(LocalDateTime.class)
        )).thenReturn(List.of(monitor));
        when(healthCheckClient.check(monitor.getUrl(), monitor.getTimeoutMillis()))
                .thenThrow(new IllegalStateException("connection failed"));

        checkService.healthCheck(now);

        var checkResultCaptor = org.mockito.ArgumentCaptor.forClass(CheckResult.class);
        verify(checkResultRepository).save(checkResultCaptor.capture());
        assertThat(checkResultCaptor.getValue().getErrorMessage()).isEqualTo("IllegalStateException");
        verify(incidentDetectionService, times(1)).detect(checkResultCaptor.getValue(), now);
    }

    @Test
    void healthCheckDoesNotNotifyAgainWhenMonitorIsAlreadyDown() {
        LocalDateTime now = LocalDateTime.of(2026, 6, 23, 12, 0);
        Monitor monitor = monitor(1, MonitorStatus.DOWN);

        when(monitorRepository.findAllByStatusInAndDeletedAtIsNullAndNextCheckAtLessThanEqual(
                eq(List.of(MonitorStatus.ACTIVE, MonitorStatus.DOWN)),
                any(LocalDateTime.class)
        )).thenReturn(List.of(monitor));
        when(healthCheckClient.check(monitor.getUrl(), monitor.getTimeoutMillis())).thenReturn(500);

        checkService.healthCheck(now);

        verify(incidentDetectionService).detect(any(CheckResult.class), eq(now));
        assertThat(monitor.getStatus()).isEqualTo(MonitorStatus.DOWN);
    }

    @Test
    void healthCheckKeepsMonitorStatusChangesOwnedByIncidentDetectionService() {
        LocalDateTime now = LocalDateTime.of(2026, 6, 23, 12, 0);
        Monitor monitor = monitor(1);

        when(monitorRepository.findAllByStatusInAndDeletedAtIsNullAndNextCheckAtLessThanEqual(
                eq(List.of(MonitorStatus.ACTIVE, MonitorStatus.DOWN)),
                any(LocalDateTime.class)
        )).thenReturn(List.of(monitor));
        when(healthCheckClient.check(monitor.getUrl(), monitor.getTimeoutMillis())).thenReturn(500);

        checkService.healthCheck(now);

        verify(incidentDetectionService).detect(any(CheckResult.class), eq(now));
        assertThat(monitor.getStatus()).isEqualTo(MonitorStatus.ACTIVE);
        assertThat(monitor.getNextCheckAt()).isEqualTo(now.plusSeconds(monitor.getIntervalSeconds()));
    }

    @Test
    void healthCheckPropagatesIncidentDetectionFailure() {
        LocalDateTime now = LocalDateTime.of(2026, 6, 23, 12, 0);
        Monitor monitor = monitor(1);

        when(monitorRepository.findAllByStatusInAndDeletedAtIsNullAndNextCheckAtLessThanEqual(
                eq(List.of(MonitorStatus.ACTIVE, MonitorStatus.DOWN)),
                any(LocalDateTime.class)
        )).thenReturn(List.of(monitor));
        when(healthCheckClient.check(monitor.getUrl(), monitor.getTimeoutMillis())).thenReturn(500);
        org.mockito.Mockito.doThrow(new IllegalStateException("detection failed"))
                .when(incidentDetectionService)
                .detect(any(CheckResult.class), eq(now));

        assertThatCode(() -> checkService.healthCheck(now))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("detection failed");

        verify(checkResultRepository).save(any());
        assertThat(monitor.getNextCheckAt()).isEqualTo(now.plusSeconds(monitor.getIntervalSeconds()));
    }

    @Test
    void handleRequestedCheckReloadsMonitorAndRunsSingleCheck() {
        LocalDateTime scheduledAt = LocalDateTime.of(2026, 6, 30, 10, 0);
        LocalDateTime now = LocalDateTime.of(2026, 6, 30, 10, 1);
        Monitor monitor = monitor(3);
        ReflectionTestUtils.setField(monitor, "id", 10L);

        when(monitorRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(monitor));
        when(healthCheckClient.check(monitor.getUrl(), monitor.getTimeoutMillis())).thenReturn(200);

        var completedEvent = checkService.handleRequestedCheck(event(10L, scheduledAt), now);

        var checkResultCaptor = org.mockito.ArgumentCaptor.forClass(CheckResult.class);
        verify(checkResultRepository).save(checkResultCaptor.capture());
        assertThat(checkResultCaptor.getValue().getStatus()).isEqualTo(CheckStatus.SUCCESS);
        assertThat(monitor.getNextCheckAt()).isEqualTo(now.plusSeconds(monitor.getIntervalSeconds()));
        assertThat(completedEvent).isPresent();
        assertThat(completedEvent.get().monitorId()).isEqualTo(10L);
        assertThat(completedEvent.get().memberId()).isEqualTo(20L);
        assertThat(completedEvent.get().status()).isEqualTo(CheckStatus.SUCCESS);
        assertThat(completedEvent.get().checkedAt()).isEqualTo(now);
    }

    @Test
    void handleRequestedCheckSkipsWhenMonitorWasAlreadyProcessed() {
        LocalDateTime scheduledAt = LocalDateTime.of(2026, 6, 30, 10, 0);
        LocalDateTime now = LocalDateTime.of(2026, 6, 30, 10, 1);
        Monitor monitor = monitor(3);
        monitor.updateNextCheckedAt(scheduledAt.plusSeconds(30));

        when(monitorRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(monitor));

        var completedEvent = checkService.handleRequestedCheck(event(10L, scheduledAt), now);

        verify(healthCheckClient, never()).check(any(), any(Integer.class));
        verify(checkResultRepository, never()).save(any(CheckResult.class));
        assertThat(completedEvent).isEmpty();
    }

    @Test
    void handleRequestedCheckSkipsWhenMonitorIsPaused() {
        LocalDateTime scheduledAt = LocalDateTime.of(2026, 6, 30, 10, 0);
        LocalDateTime now = LocalDateTime.of(2026, 6, 30, 10, 1);
        Monitor monitor = monitor(3);
        monitor.pause();

        when(monitorRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(monitor));

        var completedEvent = checkService.handleRequestedCheck(event(10L, scheduledAt), now);

        verify(healthCheckClient, never()).check(any(), any(Integer.class));
        verify(checkResultRepository, never()).save(any(CheckResult.class));
        assertThat(completedEvent).isEmpty();
    }

    private Monitor monitor(int failureThreshold) {
        return monitor(failureThreshold, MonitorStatus.ACTIVE);
    }

    private Monitor monitor(int failureThreshold, MonitorStatus status) {
        return monitor(failureThreshold, status, 1000);
    }

    private Monitor monitor(int failureThreshold, MonitorStatus status, int timeoutMillis) {
        Member member = Member.builder()
                .email("user@example.com")
                .password("password")
                .build();
        ReflectionTestUtils.setField(member, "id", 20L);

        return Monitor.builder()
                .member(member)
                .name("api")
                .url("http://localhost:9999/health")
                .intervalSeconds(5)
                .timeoutMillis(timeoutMillis)
                .failureThreshold(failureThreshold)
                .recoveryThreshold(2)
                .status(status)
                .nextCheckAt(LocalDateTime.of(2026, 6, 19, 12, 0))
                .build();
    }

    private HealthCheckRequestedEvent event(Long monitorId, LocalDateTime scheduledAt) {
        return new HealthCheckRequestedEvent(
                UUID.randomUUID(),
                scheduledAt,
                monitorId,
                20L,
                1000,
                5,
                scheduledAt,
                "SCHEDULER"
        );
    }
}
