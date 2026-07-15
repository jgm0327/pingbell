package com.monit.pingbell.check.scheduler.dispatch;

import com.monit.pingbell.check.scheduler.event.HealthCheckRequestedEvent;
import com.monit.pingbell.check.scheduler.event.HealthCheckRequestedProducer;
import com.monit.pingbell.member.domain.Member;
import com.monit.pingbell.monitor.domain.Monitor;
import com.monit.pingbell.monitor.domain.MonitorStatus;
import com.monit.pingbell.monitor.repository.MonitorRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KafkaCheckDispatchServiceTest {

    @Mock
    private MonitorRepository monitorRepository;

    @Mock
    private HealthCheckRequestedProducer producer;

    @Test
    void dispatchPublishesHealthCheckRequestedForDueMonitors() {
        LocalDateTime now = LocalDateTime.of(2026, 6, 30, 10, 0);
        LocalDateTime scheduledAt = LocalDateTime.of(2026, 6, 30, 9, 59);
        Monitor monitor = monitor(scheduledAt);

        when(monitorRepository.findAllByStatusInAndDeletedAtIsNullAndNextCheckAtLessThanEqual(
                eq(List.of(MonitorStatus.ACTIVE, MonitorStatus.DOWN)),
                eq(now)
        )).thenReturn(List.of(monitor));

        KafkaCheckDispatchService service = new KafkaCheckDispatchService(monitorRepository, producer);
        service.dispatch(now);

        ArgumentCaptor<HealthCheckRequestedEvent> eventCaptor = ArgumentCaptor.forClass(HealthCheckRequestedEvent.class);
        verify(producer).publish(eventCaptor.capture());
        assertThat(eventCaptor.getValue().monitorId()).isEqualTo(10L);
        assertThat(eventCaptor.getValue().memberId()).isEqualTo(20L);
        assertThat(eventCaptor.getValue().scheduledAt()).isEqualTo(scheduledAt);
    }

    private Monitor monitor(LocalDateTime scheduledAt) {
        Member member = Member.builder()
                .email("user@example.com")
                .password("password")
                .build();
        ReflectionTestUtils.setField(member, "id", 20L);

        Monitor monitor = Monitor.builder()
                .member(member)
                .name("api")
                .url("http://localhost:9999/health")
                .intervalSeconds(30)
                .timeoutMillis(1000)
                .failureThreshold(3)
                .recoveryThreshold(2)
                .status(MonitorStatus.ACTIVE)
                .nextCheckAt(scheduledAt)
                .build();
        ReflectionTestUtils.setField(monitor, "id", 10L);
        return monitor;
    }
}
