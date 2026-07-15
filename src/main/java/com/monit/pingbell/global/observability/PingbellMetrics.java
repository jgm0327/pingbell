package com.monit.pingbell.global.observability;

import com.monit.pingbell.check.domain.CheckStatus;
import com.monit.pingbell.notification.type.NotificationChannelType;
import com.monit.pingbell.notification.type.NotificationStatus;
import com.monit.pingbell.notification.type.NotificationType;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class PingbellMetrics {

    private final MeterRegistry meterRegistry;

    public void recordHealthCheck(CheckStatus status, Integer httpStatus, long responseTimeMs) {
        String httpStatusFamily = httpStatusFamily(httpStatus);

        meterRegistry.counter(
                "pingbell.health.check.total",
                "status", status.name(),
                "http_status_family", httpStatusFamily
        ).increment();

        Timer.builder("pingbell.health.check.response.time")
                .tag("status", status.name())
                .tag("http_status_family", httpStatusFamily)
                .register(meterRegistry)
                .record(Math.max(responseTimeMs, 0), TimeUnit.MILLISECONDS);
    }

    public void recordIncidentOpened() {
        recordIncident("opened");
    }

    public void recordIncidentResolved() {
        recordIncident("resolved");
    }

    public void recordNotificationDelivery(
            NotificationChannelType channelType,
            NotificationType notificationType,
            NotificationStatus status,
            boolean manualResend
    ) {
        meterRegistry.counter(
                "pingbell.notification.delivery.total",
                "channel_type", channelType.name(),
                "notification_type", notificationType.name(),
                "status", status.name(),
                "manual_resend", String.valueOf(manualResend)
        ).increment();
    }

    public void recordNotificationRetryAttempt(
            NotificationChannelType channelType,
            NotificationType notificationType,
            NotificationStatus resultStatus
    ) {
        meterRegistry.counter(
                "pingbell.notification.retry.attempt.total",
                "channel_type", channelType.name(),
                "notification_type", notificationType.name(),
                "result_status", resultStatus.name()
        ).increment();
    }

    private void recordIncident(String result) {
        meterRegistry.counter(
                "pingbell.incident.total",
                "result", result
        ).increment();
    }

    private String httpStatusFamily(Integer httpStatus) {
        if (httpStatus == null) {
            return "none";
        }

        return (httpStatus / 100) + "xx";
    }
}
