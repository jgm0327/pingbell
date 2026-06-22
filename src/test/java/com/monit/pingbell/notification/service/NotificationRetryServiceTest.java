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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationRetryServiceTest {

    @Mock
    private NotificationHistoryRepository historyRepository;

    @Mock
    private NotificationSender sender;

    @Test
    void retryDueHistoriesMarksSentWhenRetrySucceeds() {
        NotificationRetryService retryService = createRetryService();
        LocalDateTime now = LocalDateTime.of(2026, 6, 22, 10, 1);
        NotificationHistory history = createRetryPendingHistory(now.minusMinutes(1));

        when(historyRepository.findRetryDueHistories(now)).thenReturn(List.of(history));
        when(sender.supports(NotificationChannelType.SLACK)).thenReturn(true);

        retryService.retryDueHistories(now);

        assertThat(history.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(history.getRetryCount()).isEqualTo(1);
        assertThat(history.getSentAt()).isEqualTo(now);
        assertThat(history.getNextRetryAt()).isNull();
        verify(sender).send(eq(history.getChannel()), any(NotificationMessage.class));
    }

    @Test
    void retryDueHistoriesKeepsRetryPendingWhenRetryableFailureHasRetryLeft() {
        NotificationRetryService retryService = createRetryService();
        LocalDateTime now = LocalDateTime.of(2026, 6, 22, 10, 1);
        NotificationHistory history = createRetryPendingHistory(now.minusMinutes(1));

        when(historyRepository.findRetryDueHistories(now)).thenReturn(List.of(history));
        when(sender.supports(NotificationChannelType.SLACK)).thenReturn(true);
        org.mockito.Mockito.doThrow(serverError()).when(sender).send(eq(history.getChannel()), any(NotificationMessage.class));

        retryService.retryDueHistories(now);

        assertThat(history.getStatus()).isEqualTo(NotificationStatus.RETRY_PENDING);
        assertThat(history.getRetryCount()).isEqualTo(1);
        assertThat(history.getLastAttemptedAt()).isEqualTo(now);
        assertThat(history.getNextRetryAt()).isEqualTo(now.plusMinutes(5));
        assertThat(history.isRetryable()).isTrue();
    }

    @Test
    void retryDueHistoriesMarksFailedWhenFailureIsNonRetryable() {
        NotificationRetryService retryService = createRetryService();
        LocalDateTime now = LocalDateTime.of(2026, 6, 22, 10, 1);
        NotificationHistory history = createRetryPendingHistory(now.minusMinutes(1));

        when(historyRepository.findRetryDueHistories(now)).thenReturn(List.of(history));
        when(sender.supports(NotificationChannelType.SLACK)).thenReturn(true);
        org.mockito.Mockito.doThrow(notFound()).when(sender).send(eq(history.getChannel()), any(NotificationMessage.class));

        retryService.retryDueHistories(now);

        assertThat(history.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(history.getRetryCount()).isEqualTo(1);
        assertThat(history.getNextRetryAt()).isNull();
        assertThat(history.isRetryable()).isFalse();
        assertThat(history.getLastAttemptedAt()).isEqualTo(now);
    }

    @Test
    void retryDueHistoriesMarksFailedWhenMaxRetryCountIsReached() {
        NotificationRetryService retryService = createRetryService();
        LocalDateTime now = LocalDateTime.of(2026, 6, 22, 10, 6);
        NotificationHistory history = createRetryPendingHistory(now.minusMinutes(5));
        history.increaseRetryCount();

        when(historyRepository.findRetryDueHistories(now)).thenReturn(List.of(history));
        when(sender.supports(NotificationChannelType.SLACK)).thenReturn(true);
        org.mockito.Mockito.doThrow(serverError()).when(sender).send(eq(history.getChannel()), any(NotificationMessage.class));

        retryService.retryDueHistories(now);

        assertThat(history.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(history.getRetryCount()).isEqualTo(2);
        assertThat(history.getNextRetryAt()).isNull();
        assertThat(history.isRetryable()).isFalse();
    }

    @Test
    void retryDueHistoriesMarksFailedWhenChannelIsDisabled() {
        NotificationRetryService retryService = createRetryService();
        LocalDateTime now = LocalDateTime.of(2026, 6, 22, 10, 1);
        NotificationHistory history = createRetryPendingHistory(now.minusMinutes(1));
        history.getChannel().disable();

        when(historyRepository.findRetryDueHistories(now)).thenReturn(List.of(history));

        retryService.retryDueHistories(now);

        assertThat(history.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(history.getErrorMessage()).isEqualTo("Notification channel is disabled.");
        assertThat(history.getRetryCount()).isZero();
        assertThat(history.getNextRetryAt()).isNull();
    }

    private NotificationRetryService createRetryService() {
        return new NotificationRetryService(
                historyRepository,
                List.of(sender),
                new NotificationFailureClassifier(),
                new NotificationMessageFactory()
        );
    }

    private NotificationHistory createRetryPendingHistory(LocalDateTime nextRetryAt) {
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
                .status(MonitorStatus.DOWN)
                .nextCheckAt(nextRetryAt)
                .build();
        Incident incident = Incident.builder()
                .monitor(monitor)
                .status(IncidentStatus.OPEN)
                .startedAt(nextRetryAt.minusMinutes(5))
                .lastErrorMessage("HTTP 500")
                .build();
        NotificationChannel channel = new NotificationChannel(
                member,
                NotificationChannelType.SLACK,
                "https://hooks.slack.com/services/test"
        );
        NotificationHistory history = new NotificationHistory(incident, channel, NotificationType.INCIDENT_OPEN);
        history.markRetryPending("server error", nextRetryAt.minusMinutes(1), nextRetryAt);
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

    private HttpClientErrorException notFound() {
        return HttpClientErrorException.create(
                HttpStatus.NOT_FOUND,
                "not found",
                HttpHeaders.EMPTY,
                new byte[0],
                StandardCharsets.UTF_8
        );
    }
}
