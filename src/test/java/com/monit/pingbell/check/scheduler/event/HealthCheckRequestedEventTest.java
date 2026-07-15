package com.monit.pingbell.check.scheduler.event;

import com.monit.pingbell.member.domain.Member;
import com.monit.pingbell.monitor.domain.Monitor;
import com.monit.pingbell.monitor.domain.MonitorStatus;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.RecordComponent;
import java.time.LocalDateTime;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class HealthCheckRequestedEventTest {

    @Test
    void fromContainsMinimumFieldsWithoutMonitorUrl() {
        LocalDateTime now = LocalDateTime.of(2026, 6, 30, 10, 0);
        LocalDateTime scheduledAt = LocalDateTime.of(2026, 6, 30, 9, 59);
        Monitor monitor = monitor(scheduledAt);

        HealthCheckRequestedEvent event = HealthCheckRequestedEvent.from(monitor, now);

        assertThat(event.eventId()).isNotNull();
        assertThat(event.occurredAt()).isEqualTo(now);
        assertThat(event.monitorId()).isEqualTo(10L);
        assertThat(event.memberId()).isEqualTo(20L);
        assertThat(event.timeoutMillis()).isEqualTo(1000);
        assertThat(event.intervalSeconds()).isEqualTo(30);
        assertThat(event.scheduledAt()).isEqualTo(scheduledAt);
        assertThat(event.requestedBy()).isEqualTo("SCHEDULER");
        assertThat(Arrays.stream(HealthCheckRequestedEvent.class.getRecordComponents())
                .map(RecordComponent::getName))
                .doesNotContain("url", "email", "target", "secret");
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
