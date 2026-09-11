package com.monit.pingbell.notification.repository;

import com.monit.pingbell.incident.domain.Incident;
import com.monit.pingbell.notification.domain.NotificationChannel;
import com.monit.pingbell.notification.domain.NotificationHistory;
import com.monit.pingbell.notification.type.NotificationStatus;
import com.monit.pingbell.notification.type.NotificationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    boolean existsByIncidentIdAndNotificationType(Long incidentId, NotificationType notificationType);

    @EntityGraph(attributePaths = {"incident", "incident.monitor", "channel"})
    Page<NotificationHistory> findAllByChannelMemberId(Long memberId, Pageable pageable);

    @EntityGraph(attributePaths = {"incident", "incident.monitor", "channel"})
    Page<NotificationHistory> findAllByChannelMemberIdAndStatus(
            Long memberId,
            NotificationStatus status,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"incident", "incident.monitor", "channel"})
    @Query("""
            select history
            from NotificationHistory history
            where history.channel.member.id = :memberId
              and history.status = com.monit.pingbell.notification.type.NotificationStatus.FAILED
              and history.retryCount >= history.maxRetryCount
              and (history.errorMessage is null or history.errorMessage <> 'Notification channel is disabled.')
            """)
    Page<NotificationHistory> findRetryExhaustedFailuresByChannelMemberId(
            @Param("memberId") Long memberId,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"incident", "incident.monitor", "channel"})
    @Query("""
            select history
            from NotificationHistory history
            where history.channel.member.id = :memberId
              and history.status = com.monit.pingbell.notification.type.NotificationStatus.FAILED
              and history.errorMessage = 'Notification channel is disabled.'
            """)
    Page<NotificationHistory> findChannelDisabledFailuresByChannelMemberId(
            @Param("memberId") Long memberId,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"incident", "incident.monitor", "channel"})
    @Query("""
            select history
            from NotificationHistory history
            where history.channel.member.id = :memberId
              and history.status = com.monit.pingbell.notification.type.NotificationStatus.FAILED
              and history.retryCount < history.maxRetryCount
              and not (history.retryable = true and history.nextRetryAt is not null)
              and (history.errorMessage is null or history.errorMessage <> 'Notification channel is disabled.')
            """)
    Page<NotificationHistory> findSendFailedFailuresByChannelMemberId(
            @Param("memberId") Long memberId,
            Pageable pageable
    );

    @Query("""
            select count(history)
            from NotificationHistory history
            where history.channel.member.id = :memberId
              and history.status = com.monit.pingbell.notification.type.NotificationStatus.FAILED
              and history.errorMessage = 'Notification channel is disabled.'
              and history.createdAt >= :since
            """)
    long countChannelDisabledFailuresByChannelMemberIdAndCreatedAtGreaterThanEqual(
            @Param("memberId") Long memberId,
            @Param("since") LocalDateTime since
    );

    @Query("""
            select count(history)
            from NotificationHistory history
            where history.channel.member.id = :memberId
              and history.status = com.monit.pingbell.notification.type.NotificationStatus.FAILED
              and history.retryCount < history.maxRetryCount
              and not (history.retryable = true and history.nextRetryAt is not null)
              and (history.errorMessage is null or history.errorMessage <> 'Notification channel is disabled.')
              and history.createdAt >= :since
            """)
    long countSendFailedFailuresByChannelMemberIdAndCreatedAtGreaterThanEqual(
            @Param("memberId") Long memberId,
            @Param("since") LocalDateTime since
    );

    @Query("""
            select count(history)
            from NotificationHistory history
            where history.channel.member.id = :memberId
              and history.status = com.monit.pingbell.notification.type.NotificationStatus.FAILED
              and history.retryCount >= history.maxRetryCount
              and (history.errorMessage is null or history.errorMessage <> 'Notification channel is disabled.')
              and history.createdAt >= :since
            """)
    long countRetryExhaustedFailuresByChannelMemberIdAndCreatedAtGreaterThanEqual(
            @Param("memberId") Long memberId,
            @Param("since") LocalDateTime since
    );

    @EntityGraph(attributePaths = {"incident", "incident.monitor", "channel"})
    Optional<NotificationHistory> findByIdAndChannelMemberId(Long id, Long memberId);

    // 전역(전체 tenant) 카운트 - 운영 관측성 gauge(pingbell.notification.retry.pending.current)에서만 사용한다.
    // memberId로 스코프하지 않으므로 사용자용 API 응답에는 절대 쓰지 않는다.
    long countByStatus(NotificationStatus status);

    long countByChannelMemberIdAndStatusAndCreatedAtGreaterThanEqual(
            Long memberId,
            NotificationStatus status,
            LocalDateTime since
    );

    @Query("""
            select count(history)
            from NotificationHistory history
            where history.channel.member.id = :memberId
              and history.createdAt >= :since
              and (
                    history.status = com.monit.pingbell.notification.type.NotificationStatus.RETRY_PENDING
                    or (
                        history.status = com.monit.pingbell.notification.type.NotificationStatus.FAILED
                        and history.retryable = true
                        and history.nextRetryAt is not null
                        and history.retryCount < history.maxRetryCount
                    )
              )
            """)
    long countRetryScheduledByChannelMemberIdAndCreatedAtGreaterThanEqual(
            @Param("memberId") Long memberId,
            @Param("since") LocalDateTime since
    );

    @EntityGraph(attributePaths = {"incident", "incident.monitor", "channel"})
    @Query("""
            select history
            from NotificationHistory history
            where history.retryable = true
              and history.nextRetryAt <= :now
              and history.retryCount < history.maxRetryCount
            order by history.nextRetryAt asc, history.id asc
            """)
    List<NotificationHistory> findRetryDueHistories(@Param("now") LocalDateTime now);

    // 전역(전체 tenant) 카운트 - 운영 관측성 gauge(pingbell.notification.retry.due.current)에서만
    // 사용한다. findRetryDueHistories와 같은 조건이지만 전체 row를 읽지 않고 개수만 센다.
    @Query("""
            select count(history)
            from NotificationHistory history
            where history.retryable = true
              and history.nextRetryAt <= :now
              and history.retryCount < history.maxRetryCount
            """)
    long countRetryDueHistories(@Param("now") LocalDateTime now);
}
