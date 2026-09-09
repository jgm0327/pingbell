package com.monit.pingbell.incident.service;

import com.monit.pingbell.check.domain.CheckResult;
import com.monit.pingbell.check.repository.CheckResultRepository;
import com.monit.pingbell.check.scheduler.event.HealthCheckCompletedEvent;
import com.monit.pingbell.global.observability.PingbellMetrics;
import com.monit.pingbell.incident.domain.Incident;
import com.monit.pingbell.incident.domain.IncidentDetectionProcessedCheckResult;
import com.monit.pingbell.incident.domain.IncidentStatus;
import com.monit.pingbell.incident.repository.IncidentDetectionProcessedCheckResultRepository;
import com.monit.pingbell.incident.repository.IncidentRepository;
import com.monit.pingbell.monitor.domain.Monitor;
import com.monit.pingbell.notification.service.NotificationDispatchService;
import com.monit.pingbell.notification.type.NotificationType;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class IncidentDetectionService {
    private final CheckResultRepository checkResultRepository;
    private final IncidentRepository incidentRepository;
    private final IncidentDetectionProcessedCheckResultRepository processedRepository;
    private final NotificationDispatchService notificationDispatchService;
    private final IncidentLogAnalysisTriggerService incidentLogAnalysisTriggerService;
    private final PingbellMetrics metrics;

    @Transactional
    public void detectFromCompletedEvent(HealthCheckCompletedEvent event, LocalDateTime now) {
        CheckResult checkResult = checkResultRepository.findById(event.checkResultId())
                .orElseThrow(() -> new IllegalArgumentException("CheckResult not found. checkResultId=" + event.checkResultId()));

        Monitor monitor = checkResult.getMonitor();
        if (!Objects.equals(monitor.getId(), event.monitorId())
                || !Objects.equals(monitor.getMember().getId(), event.memberId())) {
            throw new IllegalArgumentException("HealthCheckCompleted event does not match CheckResult. checkResultId="
                    + event.checkResultId());
        }

        detectOnce(checkResult, now);
    }

    public void detect(CheckResult checkResult, LocalDateTime now) {
        applyDetection(checkResult, now);
    }

    private void detectOnce(CheckResult checkResult, LocalDateTime now) {
        if (processedRepository.existsByCheckResultId(checkResult.getId())) {
            log.info("Skip duplicate HealthCheckCompleted event. checkResultId={}", checkResult.getId());
            return;
        }

        try {
            processedRepository.saveAndFlush(new IncidentDetectionProcessedCheckResult(checkResult.getId()));
        } catch (DataIntegrityViolationException e) {
            log.info("Skip duplicate HealthCheckCompleted event after unique constraint conflict. checkResultId={}",
                    checkResult.getId());
            return;
        }

        applyDetection(checkResult, now);
    }

    private void applyDetection(CheckResult checkResult, LocalDateTime now) {
        Monitor monitor = checkResult.getMonitor();
        if (checkResult.isSuccess()) {
            monitor.recordSuccess();

            if (monitor.canRecover()) {
                Incident incident = incidentRepository
                        .findByMonitorAndStatus(monitor, IncidentStatus.OPEN)
                        .orElseThrow(() -> new IllegalStateException("Incident not found"));

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
                triggerAutomaticLogAnalysis(incident, monitor, now);
            }
        }
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
            notificationDispatchService.dispatch(incident, NotificationType.INCIDENT_OPEN, now);
        } catch (Exception ignored) {
        }
    }

    private void notifyIncidentResolved(Incident incident, LocalDateTime now) {
        try {
            notificationDispatchService.dispatch(incident, NotificationType.INCIDENT_RESOLVED, now);
        } catch (Exception ignored) {
        }
    }

    private void triggerAutomaticLogAnalysis(Incident incident, Monitor monitor, LocalDateTime now) {
        try {
            incidentLogAnalysisTriggerService.analyzeAfterIncidentOpened(
                    incident.getId(), monitor.getMember().getId(), monitor.getId(), now);
        } catch (Exception ignored) {
            // Best-effort automatic analysis - must never affect Incident detection or notifications.
        }
    }
}
