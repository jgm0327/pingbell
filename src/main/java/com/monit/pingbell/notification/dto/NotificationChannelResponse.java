package com.monit.pingbell.notification.dto;

import com.monit.pingbell.notification.domain.NotificationChannel;
import com.monit.pingbell.notification.type.NotificationChannelType;

import java.time.LocalDateTime;
import java.util.UUID;

public record NotificationChannelResponse(
        UUID publicId,
        NotificationChannelType type,
        String target,
        boolean enabled,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static NotificationChannelResponse from(NotificationChannel channel) {
        return new NotificationChannelResponse(
                channel.getPublicId(),
                channel.getType(),
                channel.getTarget(),
                channel.isEnabled(),
                channel.getCreatedAt(),
                channel.getUpdatedAt()
        );
    }
}
