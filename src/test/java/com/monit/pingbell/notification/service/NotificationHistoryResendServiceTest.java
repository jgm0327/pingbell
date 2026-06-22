package com.monit.pingbell.notification.service;

import com.monit.pingbell.incident.domain.Incident;
import com.monit.pingbell.incident.domain.IncidentStatus;
import com.monit.pingbell.member.domain.Member;
import com.monit.pingbell.monitor.domain.Monitor;
import com.monit.pingbell.monitor.domain.MonitorStatus;
import com.monit.pingbell.notification.domain.NotificationChannel;
import com.monit.pingbell.notification.domain.NotificationHistory;
import com.monit.pingbell.notification.dto.NotificationMessage;
import com.monit.pingbell.notification.repository.NotificationHistoryRepository;
import com.monit.pingbell.notification.sender.NotificationSender;
import com.monit.pingbell.notification.type.NotificationChannelType;
import com.monit.pingbell.notification.type.NotificationStatus;
import com.monit.pingbell.notification.type.NotificationType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationHistoryResendServiceTest {

    @Mock
    private NotificationHistoryRepository historyRepository;

    @Mock
    private NotificationSender sender;

    @Test
    void resendCreatesNewManualHistoryAndMarksSentWhenSendSucceeds() {
        NotificationHistoryResendService resendService = createResendService();
        LocalDateTime now = LocalDateTime.of(2026, 6, 22, 12, 0);
        NotificationHistory originalHistory = createFailedHistory(10L);

        when(historyRepository.findByIdAndChannelMemberId(10L, 1L)).thenReturn(Optional.of(originalHistory));
        when(historyRepository.save(any(NotificationHistory.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(sender.supports(NotificationChannelType.SLACK)).thenReturn(true);

        var response = resendService.resend(1L, 10L, now);

        ArgumentCaptor<NotificationHistory> historyCaptor = ArgumentCaptor.forClass(NotificationHistory.class);
        verify(historyRepository).save(historyCaptor.capture());
        NotificationHistory resendHistory = historyCaptor.getValue();

        assertThat(originalHistory.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(resendHistory.isManualResend()).isTrue();
        assertThat(resendHistory.getResendOfHistoryId()).isEqualTo(10L);
        assertThat(resendHistory.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(resendHistory.getSentAt()).isEqualTo(now);
        assertThat(response.manualResend()).isTrue();
        assertThat(response.resendOfHistoryId()).isEqualTo(10L);
        assertThat(response.channelEnabled()).isTrue();
        verify(sender).send(eq(originalHistory.getChannel()), any(NotificationMessage.class));
    }

    @Test
    void resendCreatesRetryPendingManualHistoryWhenFailureIsRetryable() {
        NotificationHistoryResendService resendService = createResendService();
        LocalDateTime now = LocalDateTime.of(2026, 6, 22, 12, 0);
        NotificationHistory originalHistory = createFailedHistory(10L);

        when(historyRepository.findByIdAndChannelMemberId(10L, 1L)).thenReturn(Optional.of(originalHistory));
        when(historyRepository.save(any(NotificationHistory.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(sender.supports(NotificationChannelType.SLACK)).thenReturn(true);
        org.mockito.Mockito.doThrow(serverError()).when(sender).send(eq(originalHistory.getChannel()), any(NotificationMessage.class));

        var response = resendService.resend(1L, 10L, now);

        assertThat(response.status()).isEqualTo(NotificationStatus.RETRY_PENDING);
        assertThat(response.manualResend()).isTrue();
        assertThat(response.resendOfHistoryId()).isEqualTo(10L);
        assertThat(response.nextRetryAt()).isEqualTo(now.plusMinutes(1));
        assertThat(response.retryable()).isTrue();
    }

    @Test
    void resendRejectsNonFailedHistory() {
        NotificationHistoryResendService resendService = createResendService();
        NotificationHistory history = createFailedHistory(10L);
        history.markSent(LocalDateTime.of(2026, 6, 22, 11, 0));

        when(historyRepository.findByIdAndChannelMemberId(10L, 1L)).thenReturn(Optional.of(history));

        assertThatThrownBy(() -> resendService.resend(1L, 10L, LocalDateTime.now()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Only failed notification histories can be resent.");
    }

    @Test
    void resendRejectsDisabledChannel() {
        NotificationHistoryResendService resendService = createResendService();
        NotificationHistory history = createFailedHistory(10L);
        history.getChannel().disable();

        when(historyRepository.findByIdAndChannelMemberId(10L, 1L)).thenReturn(Optional.of(history));

        assertThatThrownBy(() -> resendService.resend(1L, 10L, LocalDateTime.now()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Notification channel is disabled.");
    }

    @Test
    void resendReturnsNotFoundWhenHistoryDoesNotBelongToMember() {
        NotificationHistoryResendService resendService = createResendService();

        when(historyRepository.findByIdAndChannelMemberId(10L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> resendService.resend(1L, 10L, LocalDateTime.now()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Notification history not found.");
    }

    private NotificationHistoryResendService createResendService() {
        return new NotificationHistoryResendService(
                historyRepository,
                List.of(sender),
                new NotificationFailureClassifier(),
                new NotificationMessageFactory()
        );
    }

    private NotificationHistory createFailedHistory(Long historyId) {
        Member member = Member.builder()
                .email("user@example.com")
                .password("password")
                .build();
        ReflectionTestUtils.setField(member, "id", 1L);

        Monitor monitor = Monitor.builder()
                .member(member)
                .name("api")
                .url("https://api.example.com/health")
                .intervalSeconds(60)
                .timeoutMillis(1000)
                .failureThreshold(3)
                .recoveryThreshold(2)
                .status(MonitorStatus.DOWN)
                .nextCheckAt(LocalDateTime.of(2026, 6, 22, 12, 0))
                .build();
        ReflectionTestUtils.setField(monitor, "id", 20L);

        Incident incident = Incident.builder()
                .monitor(monitor)
                .status(IncidentStatus.OPEN)
                .startedAt(LocalDateTime.of(2026, 6, 22, 11, 50))
                .lastErrorMessage("HTTP 500")
                .build();
        ReflectionTestUtils.setField(incident, "id", 30L);

        NotificationChannel channel = new NotificationChannel(
                member,
                NotificationChannelType.SLACK,
                "https://hooks.slack.com/services/test"
        );
        NotificationHistory history = new NotificationHistory(incident, channel, NotificationType.INCIDENT_OPEN);
        ReflectionTestUtils.setField(history, "id", historyId);
        history.markFailed("not found", LocalDateTime.of(2026, 6, 22, 11, 55));
        return history;
    }

    private HttpServerErrorException serverError() {
        return HttpServerErrorException.create(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "server error",
                HttpHeaders.EMPTY,
                new byte[0],
                StandardCharsets.UTF_8
        );
    }
}
