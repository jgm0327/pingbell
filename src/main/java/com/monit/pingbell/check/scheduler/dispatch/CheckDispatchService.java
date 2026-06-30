package com.monit.pingbell.check.scheduler.dispatch;

import java.time.LocalDateTime;

public interface CheckDispatchService {
    void dispatch(LocalDateTime now);
}
