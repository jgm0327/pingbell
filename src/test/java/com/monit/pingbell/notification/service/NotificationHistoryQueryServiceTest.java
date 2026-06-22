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
        NotificationHistory history = new NotificationHistory(
                incident,
                channel,
                NotificationType.INCIDENT_OPEN
        );
        history.markFailed("Failed to call https://hooks.slack.com/services/secret-token");

        when(historyRepository.findAllByChannelMemberIdOrderByIdDesc(member.getId()))
                .thenReturn(List.of(history));

        var responses = historyQueryService.getHistories(member.getId());

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).channelType()).isEqualTo(NotificationChannelType.SLACK);
        assertThat(responses.get(0).maskedTarget()).isEqualTo("****oken");
        assertThat(responses.get(0).notificationType()).isEqualTo(NotificationType.INCIDENT_OPEN);
        assertThat(responses.get(0).status()).isEqualTo(NotificationStatus.FAILED);
        assertThat(responses.get(0).errorMessage()).isEqualTo("Failed to call [redacted-url]");
    }
}
