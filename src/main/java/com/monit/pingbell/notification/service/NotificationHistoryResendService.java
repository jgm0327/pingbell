package com.monit.pingbell.notification.service;

import com.monit.pingbell.notification.domain.NotificationChannel;
import com.monit.pingbell.notification.domain.NotificationHistory;
import com.monit.pingbell.notification.dto.NotificationHistoryResponse;
import com.monit.pingbell.notification.repository.NotificationHistoryRepository;
import com.monit.pingbell.notification.sender.NotificationSender;
import com.monit.pingbell.notification.type.NotificationChannelType;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationHistoryResendService {

    private final NotificationHistoryRepository historyRepository;
    private final List<NotificationSender> senders;
    private final NotificationFailureClassifier failureClassifier;
    private final NotificationMessageFactory messageFactory;

    @Transactional
    public NotificationHistoryResponse resend(Long memberId, Long historyId, LocalDateTime now) {
        NotificationHistory originalHistory = historyRepository.findByIdAndChannelMemberId(historyId, memberId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification history not found."));

        validateResendable(originalHistory);

        NotificationHistory resendHistory = historyRepository.save(NotificationHistory.manualResend(
                originalHistory.getIncident(),
                originalHistory.getChannel(),
                originalHistory.getNotificationType(),
                originalHistory.getId()
        ));

        send(resendHistory, now);
        return NotificationHistoryResponse.from(resendHistory);
    }

    private void validateResendable(NotificationHistory history) {
        if (!history.isFailed()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only failed notification histories can be resent.");
        }

        if (!history.getChannel().isEnabled()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Notification channel is disabled.");
        }
    }

    private void send(NotificationHistory history, LocalDateTime now) {
        try {
            NotificationChannel channel = history.getChannel();
            NotificationSender sender = findSender(channel.getType());
            sender.send(channel, messageFactory.create(history.getIncident(), history.getNotificationType()));
            history.markSent(now);
        } catch (Exception e) {
            NotificationFailureResult failure = failureClassifier.classify(e);
            if (failure.retryable()) {
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
