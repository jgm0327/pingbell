package com.monit.pingbell.notification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record NotificationChannelUpdateRequest(
        @NotBlank @Size(max = 500) String target
) {
}
