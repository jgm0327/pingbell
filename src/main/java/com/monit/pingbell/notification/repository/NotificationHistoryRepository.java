package com.monit.pingbell.notification.repository;

import com.monit.pingbell.incident.domain.Incident;
import com.monit.pingbell.notification.domain.NotificationChannel;
import com.monit.pingbell.notification.domain.NotificationHistory;
import com.monit.pingbell.notification.type.NotificationType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface NotificationHistoryRepository extends JpaRepository<NotificationHistory, Long> {

    boolean existsByIncidentAndChannelAndNotificationType(
            Incident incident,
            NotificationChannel channel,
            NotificationType notificationType
    );

    @EntityGraph(attributePaths = {"incident", "incident.monitor", "channel"})
    List<NotificationHistory> findAllByChannelMemberIdOrderByIdDesc(Long memberId);

    @EntityGraph(attributePaths = {"incident", "incident.monitor", "channel"})
    Optional<NotificationHistory> findByIdAndChannelMemberId(Long id, Long memberId);

    @EntityGraph(attributePaths = {"incident", "incident.monitor", "channel"})
    @Query("""
            select history
            from NotificationHistory history
            where history.status = com.monit.pingbell.notification.type.NotificationStatus.RETRY_PENDING
              and history.nextRetryAt <= :now
              and history.retryCount < history.maxRetryCount
            order by history.nextRetryAt asc, history.id asc
            """)
    List<NotificationHistory> findRetryDueHistories(@Param("now") LocalDateTime now);
}
