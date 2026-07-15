package com.monit.pingbell.check.scheduler.dispatch;

import com.monit.pingbell.check.scheduler.event.HealthCheckRequestedEvent;
import com.monit.pingbell.check.scheduler.event.HealthCheckRequestedProducer;
import com.monit.pingbell.monitor.domain.Monitor;
import com.monit.pingbell.monitor.domain.MonitorStatus;
import com.monit.pingbell.monitor.repository.MonitorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "pingbell.check.dispatch-mode", havingValue = "kafka")
public class KafkaCheckDispatchService implements CheckDispatchService {
    private final MonitorRepository monitorRepository;
    private final HealthCheckRequestedProducer producer;

    @Override
    @Transactional(readOnly = true)
    public void dispatch(LocalDateTime now) {
        List<Monitor> monitors = monitorRepository
                .findAllByStatusInAndDeletedAtIsNullAndNextCheckAtLessThanEqual(List.of(MonitorStatus.ACTIVE, MonitorStatus.DOWN), now);

        for (Monitor monitor : monitors) {
            producer.publish(HealthCheckRequestedEvent.from(monitor, now));
        }
    }
}
