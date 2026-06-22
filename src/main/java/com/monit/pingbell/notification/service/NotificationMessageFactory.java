package com.monit.pingbell.notification.service;

import com.monit.pingbell.incident.domain.Incident;
import com.monit.pingbell.monitor.domain.Monitor;
import com.monit.pingbell.notification.dto.NotificationMessage;
import com.monit.pingbell.notification.type.NotificationType;
import org.springframework.stereotype.Component;

@Component
public class NotificationMessageFactory {

    public NotificationMessage create(Incident incident, NotificationType type) {
        Monitor monitor = incident.getMonitor();
        return switch (type) {
            case INCIDENT_OPEN -> new NotificationMessage(
                    type,
                    "[Pingbell] 장애 발생: " + monitor.getName(),
                    """
                            모니터링 대상에 장애가 발생했습니다.

                            Monitor: %s
                            URL: %s
                            Status: %s
                            Started At: %s
                            Last Error: %s
                            """.formatted(
                            monitor.getName(),
                            monitor.getUrl(),
                            incident.getStatus(),
                            incident.getStartedAt(),
                            blankToDefault(incident.getLastErrorMessage(), "-")
                    )
            );
            case INCIDENT_RESOLVED -> new NotificationMessage(
                    type,
                    "[Pingbell] 장애 복구: " + monitor.getName(),
                    """
                            모니터링 대상이 복구되었습니다.

                            Monitor: %s
                            URL: %s
                            Status: %s
                            Started At: %s
                            Resolved At: %s
                            """.formatted(
                            monitor.getName(),
                            monitor.getUrl(),
                            incident.getStatus(),
                            incident.getStartedAt(),
                            incident.getResolvedAt()
                    )
            );
        };
    }

    private String blankToDefault(String value, String defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return value;
    }
}
