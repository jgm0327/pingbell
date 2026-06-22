package com.monit.pingbell.notification.sender;

import com.monit.pingbell.notification.domain.NotificationChannel;
import com.monit.pingbell.notification.dto.NotificationMessage;
import com.monit.pingbell.notification.type.NotificationChannelType;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Component
public class DiscordNotificationSender implements NotificationSender {

    private final RestClient restClient;

    public DiscordNotificationSender(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.build();
    }

    @Override
    public boolean supports(NotificationChannelType type) {
        return type == NotificationChannelType.DISCORD;
    }

    @Override
    public void send(NotificationChannel channel, NotificationMessage message) {
        restClient.post()
                .uri(channel.getTarget())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("content", formatContent(message)))
                .retrieve()
                .toBodilessEntity();
    }

    private String formatContent(NotificationMessage message) {
        return "**%s**\n%s".formatted(message.title(), message.body());
    }
}
