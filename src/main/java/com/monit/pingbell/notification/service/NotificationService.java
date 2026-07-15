package com.monit.pingbell.notification.service;

import com.monit.pingbell.incident.domain.Incident;
import com.monit.pingbell.global.observability.PingbellMetrics;
import com.monit.pingbell.monitor.domain.Monitor;
import com.monit.pingbell.notification.domain.NotificationChannel;
import com.monit.pingbell.notification.domain.NotificationHistory;
import com.monit.pingbell.notification.repository.NotificationChannelRepository;
import com.monit.pingbell.notification.repository.NotificationHistoryRepository;
import com.monit.pingbell.notification.sender.NotificationSender;
import com.monit.pingbell.notification.type.NotificationChannelType;
import com.monit.pingbell.notification.type.NotificationType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationService {
    private final NotificationChannelRepository channelRepository;
    private final NotificationHistoryRepository historyRepository;
    private final List<NotificationSender> senders;
    private final NotificationFailureClassifier failureClassifier;
    private final NotificationMessageFactory messageFactory;
    private final PingbellMetrics metrics;
    private final NotificationRetryPolicy retryPolicy;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void notifyIncidentOpened(Incident incident, LocalDateTime now) {
        notify(incident, NotificationType.INCIDENT_OPEN, now);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void notifyIncidentResolved(Incident incident, LocalDateTime now) {
        notify(incident, NotificationType.INCIDENT_RESOLVED, now);
    }

    private void notify(Incident incident, NotificationType type, LocalDateTime now) {
        Monitor monitor = incident.getMonitor();
        List<NotificationChannel> channels = findEnabledChannelsOrCreateDefaultEmail(monitor);

        for (NotificationChannel channel : channels) {
            if (historyRepository.existsByIncidentAndChannelAndNotificationType(incident, channel, type)) {
                continue;
            }

            NotificationHistory history = new NotificationHistory(incident, channel, type);
            history.changeMaxRetryCount(retryPolicy.maxRetryCount(type));
            history = historyRepository.save(history);
            try {
                NotificationSender sender = findSender(channel.getType());
                sender.send(channel, messageFactory.create(incident, type));
                history.markSent(now);
                metrics.recordNotificationDelivery(channel.getType(), type, history.getStatus(), history.isManualResend());
            } catch (Exception e) {
                handleInitialSendFailure(history, channel, type, now, e);
                metrics.recordNotificationDelivery(channel.getType(), type, history.getStatus(), history.isManualResend());
            }
        }
    }

    private void handleInitialSendFailure(
            NotificationHistory history,
            NotificationChannel channel,
            NotificationType type,
            LocalDateTime now,
            Exception e
    ) {
        NotificationFailureResult failure = failureClassifier.classify(e);
        if (!failure.retryable()) {
            history.markFailed(failure.errorMessage(), now);
            return;
        }

        if (retryPolicy.shouldRetryImmediatelyOnInitialFailure(type)) {
            retryImmediately(history, channel, type, now, failure);
            return;
        }

        history.markRetryScheduledFailure(
                failure.errorMessage(),
                now,
                retryPolicy.nextRetryAt(type, history.getRetryCount(), now)
        );
    }

    private void retryImmediately(
            NotificationHistory history,
            NotificationChannel channel,
            NotificationType type,
            LocalDateTime now,
            NotificationFailureResult firstFailure
    ) {
        history.increaseRetryCount();
        try {
            NotificationSender sender = findSender(channel.getType());
            sender.send(channel, messageFactory.create(history.getIncident(), type));
            history.markSent(now);
        } catch (Exception retryException) {
            NotificationFailureResult retryFailure = failureClassifier.classify(retryException);
            if (retryFailure.retryable() && history.getRetryCount() < history.getMaxRetryCount()) {
                history.markRetryScheduledFailure(
                        retryFailure.errorMessage(),
                        now,
                        retryPolicy.nextRetryAt(type, history.getRetryCount(), now)
                );
                return;
            }
            String errorMessage = retryFailure.retryable()
                    ? retryFailure.errorMessage()
                    : firstFailure.errorMessage() + " / immediate retry failed: " + retryFailure.errorMessage();
            history.markFailed(errorMessage, now);
        }
    }

    private List<NotificationChannel> findEnabledChannelsOrCreateDefaultEmail(Monitor monitor) {
        List<NotificationChannel> channels = channelRepository.findAllByMemberAndEnabledTrue(monitor.getMember());
        if (!channels.isEmpty()) {
            return channels;
        }

        if (!channelRepository.findAllByMemberIdOrderByIdDesc(monitor.getMember().getId()).isEmpty()) {
            return channels;
        }

        NotificationChannel defaultChannel = channelRepository.save(new NotificationChannel(
                monitor.getMember(),
                NotificationChannelType.EMAIL,
                monitor.getMember().getEmail()
        ));
        return List.of(defaultChannel);
    }

    private NotificationSender findSender(NotificationChannelType type) {
        return senders.stream()
                .filter(sender -> sender.supports(type))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Unsupported notification channel type: " + type));
    }

}
