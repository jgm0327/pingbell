package com.monit.pingbell.check.service;

import com.monit.pingbell.check.client.HealthCheckClient;
import com.monit.pingbell.check.domain.CheckResult;
import com.monit.pingbell.check.domain.CheckStatus;
import com.monit.pingbell.check.repository.CheckResultRepository;
import com.monit.pingbell.incident.domain.Incident;
import com.monit.pingbell.incident.domain.IncidentStatus;
import com.monit.pingbell.incident.repository.IncidentRepository;
import com.monit.pingbell.monitor.domain.Monitor;
import com.monit.pingbell.monitor.domain.MonitorStatus;
import com.monit.pingbell.monitor.repository.MonitorRepository;
import com.monit.pingbell.notification.service.NotificationService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CheckService {
    private final MonitorRepository monitorRepository;
    private final HealthCheckClient healthCheckClient;
    private final CheckResultRepository checkResultRepository;
    private final IncidentRepository incidentRepository;
    private final NotificationService notificationService;

    @Transactional
    public void healthCheck(LocalDateTime now) {
        List<Monitor> monitors = monitorRepository
                .findAllByStatusInAndDeletedAtIsNullAndNextCheckAtLessThanEqual(List.of(MonitorStatus.ACTIVE, MonitorStatus.DOWN), now);

        for (Monitor monitor : monitors) {
            CheckResult checkResult = executeOnce(monitor);
            checkResultRepository.save(checkResult);
            if (checkResult.isSuccess()) {
                monitor.recordSuccess();

                if (monitor.canRecover()) {
                    Incident incident = incidentRepository
                            .findByMonitorAndStatus(monitor, IncidentStatus.OPEN)
                            .orElseThrow(() -> new RuntimeException("Incident not found"));

                    incident.resolve(now);
                    monitor.recover();
                    notifyIncidentResolved(incident, now);
                }
            } else {
                monitor.recordFailure();

                if (monitor.canOpenIncident() && !incidentRepository.existsByMonitorAndStatus(monitor, IncidentStatus.OPEN)) {
                    Incident incident = incidentRepository.save(Incident.builder()
                            .status(IncidentStatus.OPEN)
                            .lastErrorMessage(checkResult.getErrorMessage())
                            .startedAt(now)
                            .monitor(monitor)
                            .build());
                    monitor.markDown();
                    notifyIncidentOpened(incident, now);
                }
            }
            monitor.updateNextCheckedAt(now.plusSeconds(monitor.getIntervalSeconds()));
        }
    }

    private CheckResult executeOnce(Monitor monitor) {
        long startTime = System.nanoTime();
        Integer statusCode = null;
        try {
            statusCode = healthCheckClient.check(monitor.getUrl(), monitor.getTimeoutMillis());
            long elapsedMs = System.nanoTime() - startTime;

            CheckStatus status = classify(statusCode, null);
            return CheckResult.builder()
                    .httpStatus(statusCode)
                    .responseTimeMs(elapsedMs / 1_000_000)
                    .monitor(monitor)
                    .status(status)
                    .build();

        } catch (Exception e) {
            long elapsedMs = System.nanoTime() - startTime;

            CheckStatus status = classify(null, e);
            return CheckResult.builder()
                    .responseTimeMs(elapsedMs / 1_000_000)
                    .monitor(monitor)
                    .status(status)
                    .httpStatus(statusCode)
                    .errorMessage(e.getClass().getSimpleName())
                    .build();
        }
    }

    private CheckStatus classify(Integer statusCode, Throwable e) {
        if (isTimeout(e)) return CheckStatus.TIMEOUT;
        if (statusCode == null) return CheckStatus.FAILURE;

        HttpStatus.Series series = HttpStatus.Series.valueOf(statusCode);

        return switch (series) {
            case INFORMATIONAL, SUCCESSFUL, REDIRECTION -> CheckStatus.SUCCESS;
            case SERVER_ERROR, CLIENT_ERROR -> CheckStatus.FAILURE;
        };
    }

    private boolean isTimeout(Throwable error) {
        if (error == null) return false;
        return error instanceof SocketTimeoutException
                || error instanceof HttpTimeoutException
                || error.getCause() instanceof SocketTimeoutException;
    }

    private void notifyIncidentOpened(Incident incident, LocalDateTime now) {
        try {
            notificationService.notifyIncidentOpened(incident, now);
        } catch (Exception ignored) {
        }
    }

    private void notifyIncidentResolved(Incident incident, LocalDateTime now) {
        try {
            notificationService.notifyIncidentResolved(incident, now);
        } catch (Exception ignored) {
        }
    }
}
