package com.monit.pingbell.global.observability;

import com.monit.pingbell.check.domain.CheckStatus;
import com.monit.pingbell.incident.domain.IncidentStatus;
import com.monit.pingbell.incident.repository.IncidentRepository;
import com.monit.pingbell.notification.repository.NotificationHistoryRepository;
import com.monit.pingbell.notification.type.NotificationChannelType;
import com.monit.pingbell.notification.type.NotificationStatus;
import com.monit.pingbell.notification.type.NotificationType;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class PingbellMetrics {

    private final MeterRegistry meterRegistry;
    private final IncidentRepository incidentRepository;
    private final NotificationHistoryRepository notificationHistoryRepository;

    // counter/timer와 달리 gauge는 "지금 이 순간의 상태"라서 이벤트 시점에 기록하지 않고,
    // 조회될 때마다 DB를 다시 세도록 등록만 해둔다(docs/operations/observability-metrics.md 12절).
    @PostConstruct
    void registerStateGauges() {
        Gauge.builder("pingbell.incident.open.current", incidentRepository,
                        repository -> repository.countByStatus(IncidentStatus.OPEN))
                .description("현재 open 상태인 incident 수(전체 tenant 합계)")
                .register(meterRegistry);

        Gauge.builder("pingbell.notification.retry.pending.current", notificationHistoryRepository,
                        repository -> repository.countByStatus(NotificationStatus.RETRY_PENDING))
                .description("현재 재시도 대기(RETRY_PENDING) 상태인 알림 이력 수(전체 tenant 합계)")
                .register(meterRegistry);

        // RETRY_PENDING(위 gauge)과는 다른 값이다 - 이건 FAILED 상태이면서 nextRetryAt이 이미
        // 지나 다음 스케줄러 실행 때 재시도될 이력 수(NotificationHistoryRepository.findRetryDueHistories
        // 와 동일한 조건). now는 조회 시점마다 새로 계산해야 하므로 람다 안에서 호출한다.
        Gauge.builder("pingbell.notification.retry.due.current", notificationHistoryRepository,
                        repository -> repository.countRetryDueHistories(LocalDateTime.now()))
                .description("현재 재시도 대상(nextRetryAt 도래) 알림 이력 수(전체 tenant 합계)")
                .register(meterRegistry);
    }

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

    // 재시도를 다 소진하고(retryCount >= maxRetryCount) 최종 실패로 확정되는 시점에만 호출한다.
    // NotificationHistory.resolveFailureType()이 RETRY_EXHAUSTED로 판정한 경우와 항상 일치해야
    // 하므로, 호출부에서 직접 조건을 재구현하지 말고 그 결과를 그대로 사용한다.
    public void recordNotificationRetryExhausted(NotificationChannelType channelType, NotificationType notificationType) {
        meterRegistry.counter(
                "pingbell.notification.retry.exhausted.total",
                "channel_type", channelType.name(),
                "notification_type", notificationType.name()
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
