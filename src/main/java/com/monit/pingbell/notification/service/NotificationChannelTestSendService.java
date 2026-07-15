package com.monit.pingbell.notification.service;

import com.monit.pingbell.notification.domain.NotificationChannel;
import com.monit.pingbell.notification.dto.NotificationChannelTestSendResponse;
import com.monit.pingbell.notification.dto.NotificationMessage;
import com.monit.pingbell.notification.repository.NotificationChannelRepository;
import com.monit.pingbell.notification.sender.NotificationSender;
import com.monit.pingbell.notification.type.NotificationChannelType;
import com.monit.pingbell.notification.type.NotificationType;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationChannelTestSendService {

    private static final String TEST_TITLE = "[Pingbell] Notification channel test";
    private static final String TEST_BODY = "This is a test notification from Pingbell.";

    private final NotificationChannelRepository channelRepository;
    private final List<NotificationSender> senders;

    @Transactional(readOnly = true)
    public NotificationChannelTestSendResponse sendTest(Long memberId, UUID publicId) {
        NotificationChannel channel = channelRepository.findByPublicIdAndMemberId(publicId, memberId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification channel not found."));

        if (!channel.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Disabled notification channels cannot send test notifications.");
        }

        LocalDateTime testedAt = LocalDateTime.now();
        try {
            findSender(channel.getType()).send(
                    channel,
                    new NotificationMessage(NotificationType.INCIDENT_OPEN, TEST_TITLE, TEST_BODY)
            );
            return new NotificationChannelTestSendResponse(
                    channel.getPublicId(),
                    channel.getType(),
                    true,
                    "Test notification sent.",
                    testedAt
            );
        } catch (Exception e) {
            return new NotificationChannelTestSendResponse(
                    channel.getPublicId(),
                    channel.getType(),
                    false,
                    failureMessage(e),
                    testedAt
            );
        }
    }

    private NotificationSender findSender(NotificationChannelType type) {
        return senders.stream()
                .filter(sender -> sender.supports(type))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Unsupported notification channel type: " + type));
    }

    private String failureMessage(Exception e) {
        String message = e.getMessage();
        if (message == null || message.isBlank()) {
            return "Test notification failed.";
        }
        return "Test notification failed: " + message;
    }
}
