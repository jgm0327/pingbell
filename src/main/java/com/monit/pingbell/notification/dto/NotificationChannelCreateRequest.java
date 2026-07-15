package com.monit.pingbell.notification.dto;

import com.monit.pingbell.notification.type.NotificationChannelType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record NotificationChannelCreateRequest(
        @NotNull NotificationChannelType type,
        @NotBlank @Size(max = 500) String target
) {
}
