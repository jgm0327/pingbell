package com.monit.pingbell.check;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class CheckScheduler {
    private final Clock clock;
    private final CheckService checkService;

    @Scheduled(fixedDelay = 5000)
    public void check() {
        LocalDateTime now = LocalDateTime.now(clock);
        checkService.healthCheck(now);
    }
}
