package com.monit.pingbell.notification.domain;

import com.monit.pingbell.incident.domain.Incident;
import com.monit.pingbell.incident.domain.IncidentStatus;
import com.monit.pingbell.member.domain.Member;
import com.monit.pingbell.monitor.domain.Monitor;
import com.monit.pingbell.monitor.domain.MonitorStatus;
import com.monit.pingbell.notification.type.NotificationChannelType;
import com.monit.pingbell.notification.type.NotificationStatus;
import com.monit.pingbell.notification.type.NotificationType;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationHistoryTest {

    @Test
    void newHistoryHasRetryDefaults() {
        NotificationHistory history = createHistory();

        assertThat(history.getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(history.getRetryCount()).isZero();
        assertThat(history.getMaxRetryCount()).isEqualTo(2);
        assertThat(history.getNextRetryAt()).isNull();
        assertThat(history.getLastAttemptedAt()).isNull();
        assertThat(history.isRetryable()).isFalse();
        assertThat(history.isManualResend()).isFalse();
        assertThat(history.getResendOfHistoryId()).isNull();
    }

    @Test
    void manualResendHistoryStoresOriginalHistoryId() {
        NotificationHistory history = createHistory();

        NotificationHistory manualResend = NotificationHistory.manualResend(
                history.getIncident(),
                history.getChannel(),
                history.getNotificationType(),
                10L
        );

        assertThat(manualResend.getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(manualResend.isManualResend()).isTrue();
        assertThat(manualResend.getResendOfHistoryId()).isEqualTo(10L);
    }

    @Test
    void markRetryPendingStoresRetrySchedule() {
        NotificationHistory history = createHistory();
        LocalDateTime attemptedAt = LocalDateTime.of(2026, 6, 22, 10, 0);
        LocalDateTime nextRetryAt = attemptedAt.plusMinutes(1);

        history.markRetryPending("timeout", attemptedAt, nextRetryAt);

        assertThat(history.isRetryPending()).isTrue();
        assertThat(history.getRetryCount()).isZero();
        assertThat(history.getLastAttemptedAt()).isEqualTo(attemptedAt);
        assertThat(history.getNextRetryAt()).isEqualTo(nextRetryAt);
        assertThat(history.isRetryable()).isTrue();
        assertThat(history.getErrorMessage()).isEqualTo("timeout");
    }

    @Test
    void markSentClearsRetrySchedule() {
        NotificationHistory history = createHistory();
        LocalDateTime attemptedAt = LocalDateTime.of(2026, 6, 22, 10, 0);
        LocalDateTime nextRetryAt = attemptedAt.plusMinutes(1);
        LocalDateTime sentAt = attemptedAt.plusSeconds(10);
        history.markRetryPending("timeout", attemptedAt, nextRetryAt);

        history.markSent(sentAt);

        assertThat(history.isSent()).isTrue();
        assertThat(history.getSentAt()).isEqualTo(sentAt);
        assertThat(history.getLastAttemptedAt()).isEqualTo(sentAt);
        assertThat(history.getNextRetryAt()).isNull();
        assertThat(history.isRetryable()).isFalse();
        assertThat(history.getErrorMessage()).isNull();
    }

    private NotificationHistory createHistory() {
        Member member = Member.builder()
                .email("user@example.com")
                .password("password")
                .build();
        Monitor monitor = Monitor.builder()
                .member(member)
                .name("api")
                .url("https://api.example.com/health")
                .intervalSeconds(60)
                .timeoutMillis(1000)
                .failureThreshold(3)
                .recoveryThreshold(2)
                .status(MonitorStatus.ACTIVE)
                .nextCheckAt(LocalDateTime.of(2026, 6, 22, 9, 0))
                .build();
        Incident incident = Incident.builder()
                .monitor(monitor)
                .status(IncidentStatus.OPEN)
                .startedAt(LocalDateTime.of(2026, 6, 22, 9, 30))
                .lastErrorMessage("HTTP 500")
                .build();
        NotificationChannel channel = new NotificationChannel(
                member,
                NotificationChannelType.SLACK,
                "https://hooks.slack.com/services/test"
        );
        return new NotificationHistory(incident, channel, NotificationType.INCIDENT_OPEN);
    }
}
