package com.monit.pingbell.notification.repository;

import com.monit.pingbell.incident.domain.Incident;
import com.monit.pingbell.notification.domain.NotificationChannel;
import com.monit.pingbell.notification.domain.NotificationHistory;
import com.monit.pingbell.notification.type.NotificationType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationHistoryRepository extends JpaRepository<NotificationHistory, Long> {

    boolean existsByIncidentAndChannelAndNotificationType(
            Incident incident,
            NotificationChannel channel,
            NotificationType notificationType
    );
}
