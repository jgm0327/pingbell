package com.monit.pingbell.notification.service;

import com.monit.pingbell.incident.domain.Incident;
import com.monit.pingbell.notification.type.NotificationType;

import java.time.LocalDateTime;

public interface NotificationDispatchService {
    void dispatch(Incident incident, NotificationType type, LocalDateTime occurredAt);
}
