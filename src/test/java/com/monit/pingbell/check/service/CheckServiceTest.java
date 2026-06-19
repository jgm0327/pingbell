package com.monit.pingbell.check.service;

import com.monit.pingbell.check.client.HealthCheckClient;
import com.monit.pingbell.check.repository.CheckResultRepository;
import com.monit.pingbell.incident.domain.Incident;
import com.monit.pingbell.incident.domain.IncidentStatus;
import com.monit.pingbell.incident.repository.IncidentRepository;
import com.monit.pingbell.member.domain.Member;
import com.monit.pingbell.monitor.domain.Monitor;
import com.monit.pingbell.monitor.domain.MonitorStatus;
import com.monit.pingbell.monitor.repository.MonitorRepository;
import com.monit.pingbell.notification.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
    private IncidentRepository incidentRepository;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private CheckService checkService;

    @Test
    void healthCheckNotifiesIncidentOpenedWhenFailureThresholdIsReached() {
        LocalDateTime now = LocalDateTime.of(2026, 6, 19, 12, 0);
        Monitor monitor = monitor(2);
        Incident incident = Incident.builder()
                .monitor(monitor)
                .status(IncidentStatus.OPEN)
                .startedAt(now)
                .lastErrorMessage(null)
                .build();

        when(monitorRepository.findAllByStatusInAndDeletedAtIsNullAndNextCheckAtLessThanEqual(
                eq(List.of(MonitorStatus.ACTIVE, MonitorStatus.DOWN)),
                any(LocalDateTime.class)
        )).thenReturn(List.of(monitor));
        when(healthCheckClient.check(monitor.getUrl(), monitor.getTimeoutMillis())).thenReturn(500);
        when(incidentRepository.existsByMonitorAndStatus(monitor, IncidentStatus.OPEN)).thenReturn(false);
        when(incidentRepository.save(any(Incident.class))).thenReturn(incident);

        checkService.healthCheck(now);
        verify(notificationService, never()).notifyIncidentOpened(any(Incident.class), any(LocalDateTime.class));

        checkService.healthCheck(now.plusSeconds(monitor.getIntervalSeconds()));
        verify(notificationService).notifyIncidentOpened(incident, now.plusSeconds(monitor.getIntervalSeconds()));
    }

    private Monitor monitor(int failureThreshold) {
        Member member = Member.builder()
                .email("user@example.com")
                .password("password")
                .build();

        return Monitor.builder()
                .member(member)
                .name("api")
                .url("http://localhost:9999/health")
                .intervalSeconds(5)
                .timeoutMillis(1000)
                .failureThreshold(failureThreshold)
                .recoveryThreshold(2)
                .status(MonitorStatus.ACTIVE)
                .nextCheckAt(LocalDateTime.of(2026, 6, 19, 12, 0))
                .build();
    }
}
