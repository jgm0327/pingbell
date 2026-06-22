package com.monit.pingbell.notification.service;

import com.monit.pingbell.incident.domain.Incident;
import com.monit.pingbell.incident.domain.IncidentStatus;
import com.monit.pingbell.member.domain.Member;
import com.monit.pingbell.monitor.domain.Monitor;
import com.monit.pingbell.monitor.domain.MonitorStatus;
import com.monit.pingbell.notification.domain.NotificationChannel;
import com.monit.pingbell.notification.domain.NotificationHistory;
import com.monit.pingbell.notification.repository.NotificationHistoryRepository;
import com.monit.pingbell.notification.type.NotificationChannelType;
import com.monit.pingbell.notification.type.NotificationStatus;
import com.monit.pingbell.notification.type.NotificationType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationHistoryQueryServiceTest {

    @Mock
    private NotificationHistoryRepository historyRepository;

    @InjectMocks
    private NotificationHistoryQueryService historyQueryService;

    @Test
    void getHistoriesReturnsMaskedTargetAndSanitizedErrorMessage() {
        Member member = Member.builder()
                .email("user@example.com")
                .password("password")
                .build();
        Monitor monitor = Monitor.builder()
                .member(member)
                .name("API 서버")
                .url("https://api.example.com/health")
                .intervalSeconds(60)
                .timeoutMillis(1000)
                .failureThreshold(3)
                .recoveryThreshold(2)
                .status(MonitorStatus.ACTIVE)
                .nextCheckAt(LocalDateTime.now())
                .build();
        Incident incident = Incident.builder()
                .monitor(monitor)
                .status(IncidentStatus.OPEN)
                .startedAt(LocalDateTime.now())
                .lastErrorMessage("timeout")
                .build();
        NotificationChannel channel = new NotificationChannel(
                member,
                NotificationChannelType.SLACK,
                "https://hooks.slack.com/services/secret-token"
        );
        channel.disable();
        NotificationHistory history = new NotificationHistory(
                incident,
                channel,
                NotificationType.INCIDENT_OPEN
        );
        LocalDateTime attemptedAt = LocalDateTime.of(2026, 6, 22, 10, 0);
        LocalDateTime nextRetryAt = attemptedAt.plusMinutes(1);
        history.markRetryPending(
                "Failed to call https://hooks.slack.com/services/secret-token",
                attemptedAt,
                nextRetryAt
        );

        PageRequest pageable = PageRequest.of(0, 20);
        when(historyRepository.findAllByChannelMemberId(member.getId(), pageable))
                .thenReturn(new PageImpl<>(List.of(history), pageable, 1));

        var responses = historyQueryService.getHistories(member.getId(), pageable);

        assertThat(responses.content()).hasSize(1);
        assertThat(responses.page()).isZero();
        assertThat(responses.size()).isEqualTo(20);
        assertThat(responses.totalElements()).isEqualTo(1);
        assertThat(responses.totalPages()).isEqualTo(1);
        assertThat(responses.content().get(0).channelType()).isEqualTo(NotificationChannelType.SLACK);
        assertThat(responses.content().get(0).channelEnabled()).isFalse();
        assertThat(responses.content().get(0).maskedTarget()).isEqualTo("****oken");
        assertThat(responses.content().get(0).notificationType()).isEqualTo(NotificationType.INCIDENT_OPEN);
        assertThat(responses.content().get(0).status()).isEqualTo(NotificationStatus.RETRY_PENDING);
        assertThat(responses.content().get(0).retryCount()).isZero();
        assertThat(responses.content().get(0).maxRetryCount()).isEqualTo(2);
        assertThat(responses.content().get(0).nextRetryAt()).isEqualTo(nextRetryAt);
        assertThat(responses.content().get(0).lastAttemptedAt()).isEqualTo(attemptedAt);
        assertThat(responses.content().get(0).retryable()).isTrue();
        assertThat(responses.content().get(0).manualResend()).isFalse();
        assertThat(responses.content().get(0).resendOfHistoryId()).isNull();
        assertThat(responses.content().get(0).errorMessage()).isEqualTo("Failed to call [redacted-url]");
    }
}
