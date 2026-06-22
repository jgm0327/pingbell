package com.monit.pingbell.notification.sender;

import com.monit.pingbell.member.domain.Member;
import com.monit.pingbell.notification.domain.NotificationChannel;
import com.monit.pingbell.notification.dto.NotificationMessage;
import com.monit.pingbell.notification.type.NotificationChannelType;
import com.monit.pingbell.notification.type.NotificationType;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class SlackNotificationSenderTest {

    @Test
    void supportsSlackChannelType() {
        SlackNotificationSender sender = new SlackNotificationSender(RestClient.builder());

        assertThat(sender.supports(NotificationChannelType.SLACK)).isTrue();
        assertThat(sender.supports(NotificationChannelType.EMAIL)).isFalse();
    }

    @Test
    void sendPostsMessageToWebhookUrl() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        SlackNotificationSender sender = new SlackNotificationSender(builder);
        NotificationChannel channel = new NotificationChannel(
                Member.builder().email("user@example.com").password("password").build(),
                NotificationChannelType.SLACK,
                "https://hooks.slack.com/services/test"
        );
        NotificationMessage message = new NotificationMessage(
                NotificationType.INCIDENT_OPEN,
                "[Pingbell] Incident opened",
                "Monitor: api"
        );

        server.expect(requestTo("https://hooks.slack.com/services/test"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {
                          "text": "*[Pingbell] Incident opened*\\nMonitor: api"
                        }
                        """))
                .andRespond(withSuccess("ok", MediaType.TEXT_PLAIN));

        sender.send(channel, message);

        server.verify();
    }
}
