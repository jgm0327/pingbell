package com.monit.pingbell.global.dlq;

import com.monit.pingbell.check.domain.CheckResult;
import com.monit.pingbell.check.repository.CheckResultRepository;
import com.monit.pingbell.check.scheduler.event.HealthCheckCompletedEvent;
import com.monit.pingbell.check.scheduler.event.HealthCheckRequestedEvent;
import com.monit.pingbell.incident.domain.Incident;
import com.monit.pingbell.incident.repository.IncidentDetectionProcessedCheckResultRepository;
import com.monit.pingbell.incident.repository.IncidentRepository;
import com.monit.pingbell.monitor.domain.Monitor;
import com.monit.pingbell.monitor.domain.MonitorStatus;
import com.monit.pingbell.monitor.repository.MonitorRepository;
import com.monit.pingbell.notification.event.NotificationRequestedEvent;
import com.monit.pingbell.notification.repository.NotificationHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DlqDryRunService {

    private final MonitorRepository monitorRepository;
    private final CheckResultRepository checkResultRepository;
    private final IncidentRepository incidentRepository;
    private final IncidentDetectionProcessedCheckResultRepository processedCheckResultRepository;
    private final NotificationHistoryRepository notificationHistoryRepository;

    @Transactional(readOnly = true)
    public DlqDryRunResult verify(Object event) {
        if (event instanceof HealthCheckRequestedEvent requestedEvent) {
            return verifyHealthCheckRequested(requestedEvent);
        }
        if (event instanceof HealthCheckCompletedEvent completedEvent) {
            return verifyHealthCheckCompleted(completedEvent);
        }
        if (event instanceof NotificationRequestedEvent notificationEvent) {
            return verifyNotificationRequested(notificationEvent);
        }
        return DlqDryRunResult.notReprocessable("Unsupported DLQ payload type: " + event.getClass().getName());
    }

    private DlqDryRunResult verifyHealthCheckRequested(HealthCheckRequestedEvent event) {
        if (event.monitorId() == null || event.memberId() == null || event.scheduledAt() == null) {
            return DlqDryRunResult.notReprocessable("HealthCheckRequested payload requires monitorId, memberId, scheduledAt");
        }

        return monitorRepository.findById(event.monitorId())
                .map(monitor -> verifyRequestedMonitor(event, monitor))
                .orElseGet(() -> DlqDryRunResult.notReprocessable("Monitor not found. monitorId=" + event.monitorId()));
    }

    private DlqDryRunResult verifyRequestedMonitor(HealthCheckRequestedEvent event, Monitor monitor) {
        DlqDryRunResult ownershipResult = verifyMonitorOwnership(event.monitorId(), event.memberId(), monitor);
        if (ownershipResult != null) {
            return ownershipResult;
        }
        if (monitor.getDeletedAt() != null) {
            return DlqDryRunResult.notReprocessable("Monitor was deleted. monitorId=" + event.monitorId());
        }
        if (monitor.getStatus() == MonitorStatus.PAUSED) {
            return DlqDryRunResult.notReprocessable("Monitor is paused. monitorId=" + event.monitorId());
        }
        if (monitor.getNextCheckAt().isAfter(event.scheduledAt())) {
            return DlqDryRunResult.alreadyProcessed(
                    "Monitor nextCheckAt is after event scheduledAt. monitorId=" + event.monitorId()
            );
        }
        return DlqDryRunResult.reprocessable("HealthCheckRequested can be retried as a dry-run decision");
    }

    private DlqDryRunResult verifyHealthCheckCompleted(HealthCheckCompletedEvent event) {
        if (event.checkResultId() == null || event.monitorId() == null || event.memberId() == null) {
            return DlqDryRunResult.notReprocessable("HealthCheckCompleted payload requires checkResultId, monitorId, memberId");
        }

        return checkResultRepository.findById(event.checkResultId())
                .map(checkResult -> verifyCompletedCheckResult(event, checkResult))
                .orElseGet(() -> DlqDryRunResult.notReprocessable(
                        "CheckResult not found. checkResultId=" + event.checkResultId()
                ));
    }

    private DlqDryRunResult verifyCompletedCheckResult(HealthCheckCompletedEvent event, CheckResult checkResult) {
        Monitor monitor = checkResult.getMonitor();
        DlqDryRunResult ownershipResult = verifyMonitorOwnership(event.monitorId(), event.memberId(), monitor);
        if (ownershipResult != null) {
            return ownershipResult;
        }
        if (!checkResult.getId().equals(event.checkResultId())) {
            return DlqDryRunResult.notReprocessable("CheckResult id does not match payload");
        }
        if (processedCheckResultRepository.existsByCheckResultId(event.checkResultId())) {
            return DlqDryRunResult.alreadyProcessed(
                    "HealthCheckCompleted already processed. checkResultId=" + event.checkResultId()
            );
        }
        return DlqDryRunResult.reprocessable("HealthCheckCompleted can be retried as a dry-run decision");
    }

    private DlqDryRunResult verifyNotificationRequested(NotificationRequestedEvent event) {
        if (event.incidentId() == null
                || event.monitorId() == null
                || event.memberId() == null
                || event.notificationType() == null) {
            return DlqDryRunResult.notReprocessable(
                    "NotificationRequested payload requires incidentId, monitorId, memberId, notificationType"
            );
        }

        return incidentRepository.findById(event.incidentId())
                .map(incident -> verifyNotificationIncident(event, incident))
                .orElseGet(() -> DlqDryRunResult.notReprocessable("Incident not found. incidentId=" + event.incidentId()));
    }

    private DlqDryRunResult verifyNotificationIncident(NotificationRequestedEvent event, Incident incident) {
        Monitor monitor = incident.getMonitor();
        DlqDryRunResult ownershipResult = verifyMonitorOwnership(event.monitorId(), event.memberId(), monitor);
        if (ownershipResult != null) {
            return ownershipResult;
        }
        if (!incident.getId().equals(event.incidentId())) {
            return DlqDryRunResult.notReprocessable("Incident id does not match payload");
        }
        if (notificationHistoryRepository.existsByIncidentIdAndNotificationType(event.incidentId(), event.notificationType())) {
            return DlqDryRunResult.alreadyProcessed(
                    "NotificationRequested already has history. incidentId=" + event.incidentId()
            );
        }
        return DlqDryRunResult.reprocessable("NotificationRequested can be retried as a dry-run decision");
    }

    private DlqDryRunResult verifyMonitorOwnership(Long monitorId, Long memberId, Monitor monitor) {
        if (!monitor.getId().equals(monitorId)) {
            return DlqDryRunResult.notReprocessable("Payload monitorId does not match source entity. monitorId=" + monitorId);
        }
        if (monitor.getMember().getId() != memberId) {
            return DlqDryRunResult.notReprocessable("Payload memberId does not match monitor owner. memberId=" + memberId);
        }
        return null;
    }
}
