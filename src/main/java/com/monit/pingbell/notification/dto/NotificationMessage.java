package com.monit.pingbell.notification.dto;

import com.monit.pingbell.notification.type.NotificationType;

public record NotificationMessage(
        NotificationType type,
        String title,
        String body
) {
}
