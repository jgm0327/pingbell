package com.monit.pingbell.check.service;

import com.monit.pingbell.check.client.HealthCheckClient;
import com.monit.pingbell.check.domain.CheckResult;
import com.monit.pingbell.check.domain.CheckStatus;
import com.monit.pingbell.check.repository.CheckResultRepository;
import com.monit.pingbell.incident.domain.Incident;
import com.monit.pingbell.incident.domain.IncidentStatus;
import com.monit.pingbell.incident.repository.IncidentRepository;
import com.monit.pingbell.global.observability.PingbellMetrics;
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
    private final PingbellMetrics metrics;

    @Transactional
    public void healthCheck(LocalDateTime now) {
        List<Monitor> monitors = monitorRepository
                .findAllByStatusInAndDeletedAtIsNullAndNextCheckAtLessThanEqual(List.of(MonitorStatus.ACTIVE, MonitorStatus.DOWN), now);

        for (Monitor monitor : monitors) {
            CheckResult checkResult = executeOnce(monitor);
            checkResultRepository.save(checkResult);
            metrics.recordHealthCheck(checkResult.getStatus(), checkResult.getHttpStatus(), checkResult.getResponseTimeMs());
            if (checkResult.isSuccess()) {
                monitor.recordSuccess();

                if (monitor.canRecover()) {
                    Incident incident = incidentRepository
                            .findByMonitorAndStatus(monitor, IncidentStatus.OPEN)
                            .orElseThrow(() -> new RuntimeException("Incident not found"));

                    incident.resolve(now);
                    monitor.recover();
                    metrics.recordIncidentResolved();
                    notifyIncidentResolved(incident, now);
                }
            } else {
                monitor.recordFailure();

                if (monitor.canOpenIncident() && !incidentRepository.existsByMonitorAndStatus(monitor, IncidentStatus.OPEN)) {
                    Incident incident = incidentRepository.save(Incident.builder()
                            .status(IncidentStatus.OPEN)
                            .lastErrorMessage(toIncidentReason(checkResult))
                            .startedAt(now)
                            .monitor(monitor)
                            .build());
                    monitor.markDown();
                    metrics.recordIncidentOpened();
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
            long elapsedMs = (System.nanoTime() - startTime) / 1_000_000;

            CheckStatus status = classify(statusCode, null, elapsedMs, monitor.getTimeoutMillis());
            return CheckResult.builder()
                    .httpStatus(statusCode)
                    .responseTimeMs(elapsedMs)
                    .monitor(monitor)
                    .status(status)
                    .build();

        } catch (Exception e) {
            long elapsedMs = (System.nanoTime() - startTime) / 1_000_000;

            CheckStatus status = classify(null, e, elapsedMs, monitor.getTimeoutMillis());
            return CheckResult.builder()
                    .responseTimeMs(elapsedMs)
                    .monitor(monitor)
                    .status(status)
                    .httpStatus(statusCode)
                    .errorMessage(e.getClass().getSimpleName())
                    .build();
        }
    }

    private CheckStatus classify(Integer statusCode, Throwable e, long responseTimeMs, int timeoutMillis) {
        if (isTimeout(e)) return CheckStatus.TIMEOUT;
        if (statusCode == null) return CheckStatus.FAILURE;

        HttpStatus.Series series = HttpStatus.Series.valueOf(statusCode);

        if (series == HttpStatus.Series.CLIENT_ERROR || series == HttpStatus.Series.SERVER_ERROR) {
            return CheckStatus.HTTP_ERROR;
        }

        if (responseTimeMs > timeoutMillis) {
            return CheckStatus.SLOW_RESPONSE;
        }

        return CheckStatus.SUCCESS;
    }

    private boolean isTimeout(Throwable error) {
        if (error == null) return false;
        return error instanceof SocketTimeoutException
                || error instanceof HttpTimeoutException
                || error.getCause() instanceof SocketTimeoutException;
    }

    private String toIncidentReason(CheckResult checkResult) {
        if (checkResult.getErrorMessage() != null && !checkResult.getErrorMessage().isBlank()) {
            return checkResult.getErrorMessage();
        }

        if (checkResult.getHttpStatus() != null) {
            return "HTTP " + checkResult.getHttpStatus();
        }

        return checkResult.getStatus().name();
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
