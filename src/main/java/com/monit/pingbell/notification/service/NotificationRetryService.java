package com.monit.pingbell.notification.service;

import com.monit.pingbell.notification.domain.NotificationChannel;
import com.monit.pingbell.notification.domain.NotificationHistory;
import com.monit.pingbell.notification.repository.NotificationHistoryRepository;
import com.monit.pingbell.notification.sender.NotificationSender;
import com.monit.pingbell.notification.type.NotificationChannelType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationRetryService {

    private final NotificationHistoryRepository historyRepository;
    private final List<NotificationSender> senders;
    private final NotificationFailureClassifier failureClassifier;
    private final NotificationMessageFactory messageFactory;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void retryDueHistories(LocalDateTime now) {
        List<NotificationHistory> histories = historyRepository.findRetryDueHistories(now);
        for (NotificationHistory history : histories) {
            retry(history, now);
        }
    }

    private void retry(NotificationHistory history, LocalDateTime now) {
        NotificationChannel channel = history.getChannel();
        if (!channel.isEnabled()) {
            history.markFailed("Notification channel is disabled.", now);
            return;
        }

        history.increaseRetryCount();

        try {
            NotificationSender sender = findSender(channel.getType());
            sender.send(channel, messageFactory.create(history.getIncident(), history.getNotificationType()));
            history.markSent(now);
        } catch (Exception e) {
            NotificationFailureResult failure = failureClassifier.classify(e);
            if (failure.retryable() && history.getRetryCount() < history.getMaxRetryCount()) {
                history.markRetryPending(failure.errorMessage(), now, nextRetryAt(history, now));
                return;
            }
            history.markFailed(failure.errorMessage(), now);
        }
    }

    private NotificationSender findSender(NotificationChannelType type) {
        return senders.stream()
                .filter(sender -> sender.supports(type))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Unsupported notification channel type: " + type));
    }

    private LocalDateTime nextRetryAt(NotificationHistory history, LocalDateTime now) {
        if (history.getRetryCount() == 0) {
            return now.plusMinutes(1);
        }
        return now.plusMinutes(5);
    }
}
