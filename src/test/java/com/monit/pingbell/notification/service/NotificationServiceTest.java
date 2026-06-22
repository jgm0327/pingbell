package com.monit.pingbell.notification.service;

import com.monit.pingbell.incident.domain.Incident;
import com.monit.pingbell.incident.domain.IncidentStatus;
import com.monit.pingbell.member.domain.Member;
import com.monit.pingbell.monitor.domain.Monitor;
import com.monit.pingbell.monitor.domain.MonitorStatus;
import com.monit.pingbell.notification.domain.NotificationChannel;
import com.monit.pingbell.notification.domain.NotificationHistory;
import com.monit.pingbell.notification.dto.NotificationMessage;
import com.monit.pingbell.notification.repository.NotificationChannelRepository;
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
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationChannelRepository channelRepository;

    @Mock
    private NotificationHistoryRepository historyRepository;

    @Mock
    private NotificationSender sender;

    private NotificationService notificationService;

    @Test
    void notifyIncidentOpenedCreatesDefaultEmailChannelWhenMemberHasNoChannels() {
        notificationService = new NotificationService(
                channelRepository,
                historyRepository,
                List.of(sender),
                new NotificationFailureClassifier(),
                new NotificationMessageFactory()
        );

        LocalDateTime now = LocalDateTime.of(2026, 6, 19, 12, 0);
        Member member = Member.builder()
                .email("user@example.com")
                .password("password")
                .build();
        Monitor monitor = Monitor.builder()
                .member(member)
                .name("api")
                .url("http://localhost:9999/health")
                .intervalSeconds(5)
                .timeoutMillis(1000)
                .failureThreshold(2)
                .recoveryThreshold(2)
                .status(MonitorStatus.DOWN)
                .nextCheckAt(now)
                .build();
        Incident incident = Incident.builder()
                .monitor(monitor)
                .status(IncidentStatus.OPEN)
                .startedAt(now)
                .lastErrorMessage("HTTP 500")
                .build();

        when(channelRepository.findAllByMemberAndEnabledTrue(member)).thenReturn(List.of());
        when(channelRepository.findAllByMemberIdOrderByIdDesc(member.getId())).thenReturn(List.of());
        when(channelRepository.save(any(NotificationChannel.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(historyRepository.existsByIncidentAndChannelAndNotificationType(
                eq(incident),
                any(NotificationChannel.class),
                eq(NotificationType.INCIDENT_OPEN)
        )).thenReturn(false);
        when(historyRepository.save(any(NotificationHistory.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(sender.supports(NotificationChannelType.EMAIL)).thenReturn(true);

        notificationService.notifyIncidentOpened(incident, now);

        ArgumentCaptor<NotificationChannel> channelCaptor = ArgumentCaptor.forClass(NotificationChannel.class);
        verify(channelRepository).save(channelCaptor.capture());

        NotificationChannel channel = channelCaptor.getValue();
        assertThat(channel.getMember()).isSameAs(member);
        assertThat(channel.getType()).isEqualTo(NotificationChannelType.EMAIL);
        assertThat(channel.getTarget()).isEqualTo(member.getEmail());
        verify(sender).send(eq(channel), any(NotificationMessage.class));
    }

    @Test
    void notifyIncidentResolvedSendsSlackNotificationAndMarksHistorySent() {
        notificationService = new NotificationService(
                channelRepository,
                historyRepository,
                List.of(sender),
                new NotificationFailureClassifier(),
                new NotificationMessageFactory()
        );

        LocalDateTime startedAt = LocalDateTime.of(2026, 6, 19, 12, 0);
        LocalDateTime resolvedAt = LocalDateTime.of(2026, 6, 19, 12, 5);
        Member member = Member.builder()
                .email("user@example.com")
                .password("password")
                .build();
        Monitor monitor = Monitor.builder()
                .member(member)
                .name("api")
                .url("http://localhost:9999/health")
                .intervalSeconds(5)
                .timeoutMillis(1000)
                .failureThreshold(2)
                .recoveryThreshold(2)
                .status(MonitorStatus.ACTIVE)
                .nextCheckAt(resolvedAt)
                .build();
        Incident incident = Incident.builder()
                .monitor(monitor)
                .status(IncidentStatus.OPEN)
                .startedAt(startedAt)
                .lastErrorMessage("HTTP 500")
                .build();
        incident.resolve(resolvedAt);
        NotificationChannel slackChannel = new NotificationChannel(member, NotificationChannelType.SLACK, "https://hooks.slack.com/services/test");

        when(channelRepository.findAllByMemberAndEnabledTrue(member)).thenReturn(List.of(slackChannel));
        when(historyRepository.existsByIncidentAndChannelAndNotificationType(incident, slackChannel, NotificationType.INCIDENT_RESOLVED))
                .thenReturn(false);
        when(historyRepository.save(any(NotificationHistory.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(sender.supports(NotificationChannelType.SLACK)).thenReturn(true);

        notificationService.notifyIncidentResolved(incident, resolvedAt);

        ArgumentCaptor<NotificationHistory> historyCaptor = ArgumentCaptor.forClass(NotificationHistory.class);
        verify(historyRepository).save(historyCaptor.capture());
        NotificationHistory history = historyCaptor.getValue();
        assertThat(history.isSent()).isTrue();
        assertThat(history.getSentAt()).isEqualTo(resolvedAt);
        verify(sender).send(eq(slackChannel), any(NotificationMessage.class));
        verify(channelRepository, never()).save(any(NotificationChannel.class));
    }

    @Test
    void notifyIncidentOpenedMarksRetryPendingWhenWebhookReturnsServerError() {
        notificationService = new NotificationService(
                channelRepository,
                historyRepository,
                List.of(sender),
                new NotificationFailureClassifier(),
                new NotificationMessageFactory()
        );
        LocalDateTime now = LocalDateTime.of(2026, 6, 22, 10, 0);
        Member member = Member.builder()
                .email("user@example.com")
                .password("password")
                .build();
        Monitor monitor = Monitor.builder()
                .member(member)
                .name("api")
                .url("http://localhost:9999/health")
                .intervalSeconds(5)
                .timeoutMillis(1000)
                .failureThreshold(2)
                .recoveryThreshold(2)
                .status(MonitorStatus.DOWN)
                .nextCheckAt(now)
                .build();
        Incident incident = Incident.builder()
                .monitor(monitor)
                .status(IncidentStatus.OPEN)
                .startedAt(now)
                .lastErrorMessage("HTTP 500")
                .build();
        NotificationChannel slackChannel = new NotificationChannel(
                member,
                NotificationChannelType.SLACK,
                "https://hooks.slack.com/services/test"
        );

        when(channelRepository.findAllByMemberAndEnabledTrue(member)).thenReturn(List.of(slackChannel));
        when(historyRepository.existsByIncidentAndChannelAndNotificationType(incident, slackChannel, NotificationType.INCIDENT_OPEN))
                .thenReturn(false);
        when(historyRepository.save(any(NotificationHistory.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(sender.supports(NotificationChannelType.SLACK)).thenReturn(true);
        org.mockito.Mockito.doThrow(HttpServerErrorException.create(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "server error",
                HttpHeaders.EMPTY,
                new byte[0],
                StandardCharsets.UTF_8
        )).when(sender).send(eq(slackChannel), any(NotificationMessage.class));

        notificationService.notifyIncidentOpened(incident, now);

        ArgumentCaptor<NotificationHistory> historyCaptor = ArgumentCaptor.forClass(NotificationHistory.class);
        verify(historyRepository).save(historyCaptor.capture());
        NotificationHistory history = historyCaptor.getValue();
        assertThat(history.getStatus()).isEqualTo(NotificationStatus.RETRY_PENDING);
        assertThat(history.isRetryable()).isTrue();
        assertThat(history.getRetryCount()).isZero();
        assertThat(history.getLastAttemptedAt()).isEqualTo(now);
        assertThat(history.getNextRetryAt()).isEqualTo(now.plusMinutes(1));
    }

    @Test
    void notifyIncidentOpenedMarksFailedWhenWebhookReturnsNotFound() {
        notificationService = new NotificationService(
                channelRepository,
                historyRepository,
                List.of(sender),
                new NotificationFailureClassifier(),
                new NotificationMessageFactory()
        );
        LocalDateTime now = LocalDateTime.of(2026, 6, 22, 10, 0);
        Member member = Member.builder()
                .email("user@example.com")
                .password("password")
                .build();
        Monitor monitor = Monitor.builder()
                .member(member)
                .name("api")
                .url("http://localhost:9999/health")
                .intervalSeconds(5)
                .timeoutMillis(1000)
                .failureThreshold(2)
                .recoveryThreshold(2)
                .status(MonitorStatus.DOWN)
                .nextCheckAt(now)
                .build();
        Incident incident = Incident.builder()
                .monitor(monitor)
                .status(IncidentStatus.OPEN)
                .startedAt(now)
                .lastErrorMessage("HTTP 500")
                .build();
        NotificationChannel slackChannel = new NotificationChannel(
                member,
                NotificationChannelType.SLACK,
                "https://hooks.slack.com/services/test"
        );

        when(channelRepository.findAllByMemberAndEnabledTrue(member)).thenReturn(List.of(slackChannel));
        when(historyRepository.existsByIncidentAndChannelAndNotificationType(incident, slackChannel, NotificationType.INCIDENT_OPEN))
                .thenReturn(false);
        when(historyRepository.save(any(NotificationHistory.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(sender.supports(NotificationChannelType.SLACK)).thenReturn(true);
        org.mockito.Mockito.doThrow(HttpClientErrorException.create(
                HttpStatus.NOT_FOUND,
                "not found",
                HttpHeaders.EMPTY,
                new byte[0],
                StandardCharsets.UTF_8
        )).when(sender).send(eq(slackChannel), any(NotificationMessage.class));

        notificationService.notifyIncidentOpened(incident, now);

        ArgumentCaptor<NotificationHistory> historyCaptor = ArgumentCaptor.forClass(NotificationHistory.class);
        verify(historyRepository).save(historyCaptor.capture());
        NotificationHistory history = historyCaptor.getValue();
        assertThat(history.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(history.isRetryable()).isFalse();
        assertThat(history.getNextRetryAt()).isNull();
        assertThat(history.getLastAttemptedAt()).isEqualTo(now);
    }
}
