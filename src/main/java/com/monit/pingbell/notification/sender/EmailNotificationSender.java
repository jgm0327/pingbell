package com.monit.pingbell.notification.sender;

import com.monit.pingbell.notification.domain.NotificationChannel;
import com.monit.pingbell.notification.dto.NotificationMessage;
import com.monit.pingbell.notification.type.NotificationChannelType;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EmailNotificationSender implements NotificationSender {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String from;

    @Override
    public boolean supports(NotificationChannelType type) {
        return type == NotificationChannelType.EMAIL;
    }

    @Override
    public void send(NotificationChannel channel, NotificationMessage message) {
        SimpleMailMessage mailMessage = new SimpleMailMessage();
        if (from != null && !from.isBlank()) {
            mailMessage.setFrom(from);
        }
        mailMessage.setTo(channel.getTarget());
        mailMessage.setSubject(message.title());
        mailMessage.setText(message.body());

        mailSender.send(mailMessage);
    }
}
