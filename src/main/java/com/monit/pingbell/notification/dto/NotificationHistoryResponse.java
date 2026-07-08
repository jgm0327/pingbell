package com.monit.pingbell.notification.dto;

import com.monit.pingbell.notification.domain.NotificationHistory;
import com.monit.pingbell.notification.type.NotificationChannelType;
import com.monit.pingbell.notification.type.NotificationFailureType;
import com.monit.pingbell.notification.type.NotificationStatus;
import com.monit.pingbell.notification.type.NotificationType;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.regex.Pattern;

public record NotificationHistoryResponse(
        Long id,
        Long incidentId,
        Long monitorId,
        String monitorName,
        UUID channelPublicId,
        NotificationChannelType channelType,
        boolean channelEnabled,
        String maskedTarget,
        NotificationType notificationType,
        NotificationStatus status,
        int retryCount,
        int maxRetryCount,
        LocalDateTime nextRetryAt,
        LocalDateTime lastAttemptedAt,
        boolean retryable,
        LocalDateTime sentAt,
        boolean manualResend,
        Long resendOfHistoryId,
        NotificationFailureType failureType,
        LocalDateTime createdAt,
        String errorMessage
) {

    private static final Pattern URL_PATTERN = Pattern.compile("https?://\\S+", Pattern.CASE_INSENSITIVE);

    public static NotificationHistoryResponse from(NotificationHistory history) {
        return new NotificationHistoryResponse(
                history.getId(),
                history.getIncident().getId(),
                history.getIncident().getMonitor().getId(),
                history.getIncident().getMonitor().getName(),
                history.getChannel().getPublicId(),
                history.getChannel().getType(),
                history.getChannel().isEnabled(),
                NotificationChannelResponse.from(history.getChannel()).maskedTarget(),
                history.getNotificationType(),
                history.getStatus(),
                history.getRetryCount(),
                history.getMaxRetryCount(),
                history.getNextRetryAt(),
                history.getLastAttemptedAt(),
                history.isRetryable(),
                history.getSentAt(),
                history.isManualResend(),
                history.getResendOfHistoryId(),
                resolveFailureType(history),
                history.getCreatedAt(),
                sanitizeErrorMessage(history.getErrorMessage())
        );
    }

    private static NotificationFailureType resolveFailureType(NotificationHistory history) {
        if (!history.isFailed()) {
            return null;
        }
        if (history.isChannelDisabledFailure()) {
            return NotificationFailureType.CHANNEL_DISABLED;
        }
        if (history.isRetryExhaustedFailure()) {
            return NotificationFailureType.RETRY_EXHAUSTED;
        }
        return NotificationFailureType.SEND_FAILED;
    }

    private static String sanitizeErrorMessage(String errorMessage) {
        if (errorMessage == null || errorMessage.isBlank()) {
            return null;
        }

        String sanitized = URL_PATTERN.matcher(errorMessage).replaceAll("[redacted-url]");
        return sanitized.length() > 500 ? sanitized.substring(0, 500) : sanitized;
    }
}
