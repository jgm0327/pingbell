package com.monit.pingbell.check.scheduler.dispatch;

import com.monit.pingbell.check.service.CheckService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "pingbell.check.dispatch-mode", havingValue = "direct", matchIfMissing = true)
public class DirectCheckDispatchService implements CheckDispatchService {
    private final CheckService checkService;

    @Override
    public void dispatch(LocalDateTime now) {
        checkService.healthCheck(now);
    }
}
