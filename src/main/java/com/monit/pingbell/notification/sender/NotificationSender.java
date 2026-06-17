package com.monit.pingbell.notification.sender;

import com.monit.pingbell.notification.domain.NotificationChannel;
import com.monit.pingbell.notification.dto.NotificationMessage;
import com.monit.pingbell.notification.type.NotificationChannelType;

public interface NotificationSender {

    boolean supports(NotificationChannelType type);

    void send(NotificationChannel channel, NotificationMessage message);
}
