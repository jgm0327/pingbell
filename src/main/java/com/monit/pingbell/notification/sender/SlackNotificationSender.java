package com.monit.pingbell.notification.sender;

import com.monit.pingbell.notification.domain.NotificationChannel;
import com.monit.pingbell.notification.dto.NotificationMessage;
import com.monit.pingbell.notification.type.NotificationChannelType;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Component
public class SlackNotificationSender implements NotificationSender {

    private final RestClient restClient;

    public SlackNotificationSender(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.build();
    }

    @Override
    public boolean supports(NotificationChannelType type) {
        return type == NotificationChannelType.SLACK;
    }

    @Override
    public void send(NotificationChannel channel, NotificationMessage message) {
        restClient.post()
                .uri(channel.getTarget())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("text", formatText(message)))
                .retrieve()
                .toBodilessEntity();
    }

    private String formatText(NotificationMessage message) {
        return "*%s*\n%s".formatted(message.title(), message.body());
    }
}
