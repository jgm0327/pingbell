package com.monit.pingbell.notification.dto;

import com.monit.pingbell.notification.domain.NotificationChannel;
import com.monit.pingbell.notification.type.NotificationChannelType;

import java.time.LocalDateTime;
import java.util.UUID;

public record NotificationChannelResponse(
        UUID publicId,
        NotificationChannelType type,
        String maskedTarget,
        boolean enabled,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static NotificationChannelResponse from(NotificationChannel channel) {
        return new NotificationChannelResponse(
                channel.getPublicId(),
                channel.getType(),
                maskTarget(channel.getTarget(), channel.getType()),
                channel.isEnabled(),
                channel.getCreatedAt(),
                channel.getUpdatedAt()
        );
    }

    private static String maskTarget(String target, NotificationChannelType type) {
        if (target == null || target.isBlank()) {
            return "";
        }

        return switch (type) {
            case EMAIL -> maskEmail(target);
            case SLACK, DISCORD -> maskWebhookUrl(target);
        };
    }

    private static String maskEmail(String email) {
        int atIndex = email.indexOf('@');
        if (atIndex <= 0) {
            return maskTail(email);
        }

        String localPart = email.substring(0, atIndex);
        String domain = email.substring(atIndex);
        String visibleLocalPart = localPart.substring(0, Math.min(2, localPart.length()));
        return visibleLocalPart + "****" + domain;
    }

    private static String maskWebhookUrl(String webhookUrl) {
        int visibleLength = Math.min(4, webhookUrl.length());
        String suffix = webhookUrl.substring(webhookUrl.length() - visibleLength);
        return "****" + suffix;
    }

    private static String maskTail(String value) {
        int visibleLength = Math.min(4, value.length());
        return "****" + value.substring(value.length() - visibleLength);
    }
}
