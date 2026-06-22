package com.monit.pingbell.notification.dto;

import com.monit.pingbell.notification.domain.NotificationHistory;
import com.monit.pingbell.notification.type.NotificationChannelType;
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
        String maskedTarget,
        NotificationType notificationType,
        NotificationStatus status,
        int retryCount,
        LocalDateTime sentAt,
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
                NotificationChannelResponse.from(history.getChannel()).maskedTarget(),
                history.getNotificationType(),
                history.getStatus(),
                history.getRetryCount(),
                history.getSentAt(),
                history.getCreatedAt(),
                sanitizeErrorMessage(history.getErrorMessage())
        );
    }

    private static String sanitizeErrorMessage(String errorMessage) {
        if (errorMessage == null || errorMessage.isBlank()) {
            return null;
        }

        String sanitized = URL_PATTERN.matcher(errorMessage).replaceAll("[redacted-url]");
        return sanitized.length() > 500 ? sanitized.substring(0, 500) : sanitized;
    }
}
