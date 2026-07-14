package com.monit.pingbell.check.service;

import com.monit.pingbell.check.client.HealthCheckClient;
import com.monit.pingbell.check.domain.CheckResult;
import com.monit.pingbell.check.domain.CheckStatus;
import com.monit.pingbell.check.repository.CheckResultRepository;
import com.monit.pingbell.check.scheduler.event.HealthCheckCompletedEvent;
import com.monit.pingbell.check.scheduler.event.HealthCheckRequestedEvent;
import com.monit.pingbell.global.observability.PingbellMetrics;
import com.monit.pingbell.incident.service.IncidentDetectionService;
import com.monit.pingbell.monitor.domain.Monitor;
import com.monit.pingbell.monitor.domain.MonitorStatus;
import com.monit.pingbell.monitor.repository.MonitorRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class CheckService {
    private final MonitorRepository monitorRepository;
    private final HealthCheckClient healthCheckClient;
    private final CheckResultRepository checkResultRepository;
    private final IncidentDetectionService incidentDetectionService;
    private final PingbellMetrics metrics;

    @Transactional
    public void healthCheck(LocalDateTime now) {
        List<Monitor> monitors = monitorRepository
                .findAllByStatusInAndDeletedAtIsNullAndNextCheckAtLessThanEqual(List.of(MonitorStatus.ACTIVE, MonitorStatus.DOWN), now);

        for (Monitor monitor : monitors) {
            CheckResult checkResult = checkMonitor(monitor, now);
            incidentDetectionService.detect(checkResult, now);
        }
    }

    @Transactional
    public Optional<HealthCheckCompletedEvent> handleRequestedCheck(HealthCheckRequestedEvent event, LocalDateTime now) {
        Monitor monitor = monitorRepository.findByIdAndDeletedAtIsNull(event.monitorId())
                .orElse(null);

        if (monitor == null) {
            log.debug("Skip HealthCheckRequested event because monitor does not exist. eventId={}, monitorId={}",
                    event.eventId(), event.monitorId());
            return Optional.empty();
        }

        if (monitor.getStatus() != MonitorStatus.ACTIVE && monitor.getStatus() != MonitorStatus.DOWN) {
            log.debug("Skip HealthCheckRequested event because monitor is not checkable. eventId={}, monitorId={}, status={}",
                    event.eventId(), event.monitorId(), monitor.getStatus());
            return Optional.empty();
        }

        if (monitor.getNextCheckAt().isAfter(event.scheduledAt())) {
            log.debug("Skip HealthCheckRequested event because request was already processed. eventId={}, monitorId={}, scheduledAt={}, nextCheckAt={}",
                    event.eventId(), event.monitorId(), event.scheduledAt(), monitor.getNextCheckAt());
            return Optional.empty();
        }

        CheckResult checkResult = checkMonitor(monitor, now);
        return Optional.of(HealthCheckCompletedEvent.from(event, checkResult, now));
    }

    private CheckResult checkMonitor(Monitor monitor, LocalDateTime now) {
        CheckResult checkResult = executeOnce(monitor);
        checkResultRepository.save(checkResult);
        metrics.recordHealthCheck(checkResult.getStatus(), checkResult.getHttpStatus(), checkResult.getResponseTimeMs());
        monitor.updateNextCheckedAt(now.plusSeconds(monitor.getIntervalSeconds()));
        return checkResult;
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

}
