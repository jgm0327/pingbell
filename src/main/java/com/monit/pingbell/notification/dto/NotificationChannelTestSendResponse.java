package com.monit.pingbell.notification.dto;

import com.monit.pingbell.notification.type.NotificationChannelType;

import java.time.LocalDateTime;
import java.util.UUID;

public record NotificationChannelTestSendResponse(
        UUID publicId,
        NotificationChannelType type,
        boolean success,
        String message,
        LocalDateTime testedAt
) {
}
