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

    private static final int DEFAULT_MAX_RETRY_COUNT = 2;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "incident_id", nullable = false, foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private Incident incident;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "channel_id", nullable = false, foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private NotificationChannel channel;

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_type", nullable = false, length = 30)
    private NotificationType notificationType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private NotificationStatus status;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "max_retry_count", nullable = false)
    private int maxRetryCount;

    @Column(name = "next_retry_at")
    private LocalDateTime nextRetryAt;

    @Column(name = "last_attempted_at")
    private LocalDateTime lastAttemptedAt;

    @Column(name = "retryable", nullable = false)
    private boolean retryable;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "manual_resend", nullable = false)
    private boolean manualResend;

    @Column(name = "resend_of_history_id")
    private Long resendOfHistoryId;

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
        this.maxRetryCount = DEFAULT_MAX_RETRY_COUNT;
        this.retryable = false;
        this.manualResend = false;
        this.resendOfHistoryId = null;
    }

    public static NotificationHistory manualResend(
            Incident incident,
            NotificationChannel channel,
            NotificationType notificationType,
            Long resendOfHistoryId
    ) {
        if (resendOfHistoryId == null) {
            throw new IllegalArgumentException("resendOfHistoryId must not be null");
        }

        NotificationHistory history = new NotificationHistory(incident, channel, notificationType);
        history.manualResend = true;
        history.resendOfHistoryId = resendOfHistoryId;
        return history;
    }

    public void markSent(LocalDateTime sentAt) {
        this.status = NotificationStatus.SENT;
        this.sentAt = sentAt;
        this.lastAttemptedAt = sentAt;
        this.nextRetryAt = null;
        this.retryable = false;
        this.errorMessage = null;
    }

    public void markFailed(String errorMessage) {
        markFailed(errorMessage, null);
    }

    public void markFailed(String errorMessage, LocalDateTime lastAttemptedAt) {
        this.status = NotificationStatus.FAILED;
        this.lastAttemptedAt = lastAttemptedAt;
        this.nextRetryAt = null;
        this.retryable = false;
        this.errorMessage = errorMessage;
    }

    public void markRetryPending(String errorMessage, LocalDateTime lastAttemptedAt, LocalDateTime nextRetryAt) {
        this.status = NotificationStatus.RETRY_PENDING;
        this.lastAttemptedAt = lastAttemptedAt;
        this.nextRetryAt = nextRetryAt;
        this.retryable = true;
        this.errorMessage = errorMessage;
    }

    public void increaseRetryCount() {
        this.retryCount++;
    }

    public void resetToPending() {
        this.status = NotificationStatus.PENDING;
        this.nextRetryAt = null;
        this.retryable = false;
        this.errorMessage = null;
    }

    public boolean isPending() {
        return this.status == NotificationStatus.PENDING;
    }

    public boolean isSent() {
        return this.status == NotificationStatus.SENT;
    }

    public boolean isRetryPending() {
        return this.status == NotificationStatus.RETRY_PENDING;
    }

    public boolean isFailed() {
        return this.status == NotificationStatus.FAILED;
    }
}
