package com.monit.pingbell.notification.service;

public record NotificationFailureResult(
        boolean retryable,
        String errorMessage
) {
}
