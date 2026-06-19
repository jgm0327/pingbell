package com.monit.pingbell.notification.service;

import com.monit.pingbell.incident.domain.Incident;
import com.monit.pingbell.monitor.domain.Monitor;
import com.monit.pingbell.notification.domain.NotificationChannel;
import com.monit.pingbell.notification.domain.NotificationHistory;
import com.monit.pingbell.notification.dto.NotificationMessage;
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

            NotificationHistory history = historyRepository.save(new NotificationHistory(incident, channel, type));
            try {
                NotificationSender sender = findSender(channel.getType());
                sender.send(channel, createMessage(incident, type));
                history.markSent(now);
            } catch (Exception e) {
                history.markFailed(toErrorMessage(e));
            }
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

    private NotificationMessage createMessage(Incident incident, NotificationType type) {
        Monitor monitor = incident.getMonitor();
        return switch (type) {
            case INCIDENT_OPEN -> new NotificationMessage(
                    type,
                    "[Pingbell] 장애 발생: " + monitor.getName(),
                    """
                            모니터링 대상에 장애가 발생했습니다.

                            Monitor: %s
                            URL: %s
                            Status: %s
                            Started At: %s
                            Last Error: %s
                            """.formatted(
                            monitor.getName(),
                            monitor.getUrl(),
                            incident.getStatus(),
                            incident.getStartedAt(),
                            blankToDefault(incident.getLastErrorMessage(), "-")
                    )
            );
            case INCIDENT_RESOLVED -> new NotificationMessage(
                    type,
                    "[Pingbell] 장애 복구: " + monitor.getName(),
                    """
                            모니터링 대상이 복구되었습니다.

                            Monitor: %s
                            URL: %s
                            Status: %s
                            Started At: %s
                            Resolved At: %s
                            """.formatted(
                            monitor.getName(),
                            monitor.getUrl(),
                            incident.getStatus(),
                            incident.getStartedAt(),
                            incident.getResolvedAt()
                    )
            );
        };
    }

    private String toErrorMessage(Exception e) {
        String message = e.getMessage();
        if (message == null || message.isBlank()) {
            return e.getClass().getSimpleName();
        }
        return message.length() > 1000 ? message.substring(0, 1000) : message;
    }

    private String blankToDefault(String value, String defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return value;
    }
}
