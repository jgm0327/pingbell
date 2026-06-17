package com.monit.pingbell.notification.domain;

import com.monit.pingbell.global.common.BaseTimeEntity;
import com.monit.pingbell.incident.domain.Incident;
import com.monit.pingbell.notification.type.NotificationStatus;
import com.monit.pingbell.notification.type.NotificationType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "notification_histories")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationHistory extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "incident_id", nullable = false)
    private Incident incident;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "channel_id", nullable = false)
    private NotificationChannel channel;

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_type", nullable = false, length = 30)
    private NotificationType notificationType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private NotificationStatus status;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    public NotificationHistory(
            Incident incident,
            NotificationChannel channel,
            NotificationType notificationType
    ) {
        this.incident = incident;
        this.channel = channel;
        this.notificationType = notificationType;
        this.status = NotificationStatus.PENDING;
        this.retryCount = 0;
    }

    public void markSent(LocalDateTime sentAt) {
        this.status = NotificationStatus.SENT;
        this.sentAt = sentAt;
        this.errorMessage = null;
    }

    public void markFailed(String errorMessage) {
        this.status = NotificationStatus.FAILED;
        this.retryCount++;
        this.errorMessage = errorMessage;
    }

    public void resetToPending() {
        this.status = NotificationStatus.PENDING;
        this.errorMessage = null;
    }

    public boolean isPending() {
        return this.status == NotificationStatus.PENDING;
    }

    public boolean isSent() {
        return this.status == NotificationStatus.SENT;
    }

    public boolean isFailed() {
        return this.status == NotificationStatus.FAILED;
    }
}
