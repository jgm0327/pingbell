package com.monit.pingbell.notification.scheduler;

import com.monit.pingbell.notification.service.NotificationRetryService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class NotificationRetryScheduler {

    private final Clock clock;
    private final NotificationRetryService retryService;

    @Scheduled(fixedDelay = 10000)
    public void retry() {
        retryService.retryDueHistories(LocalDateTime.now(clock));
    }
}
