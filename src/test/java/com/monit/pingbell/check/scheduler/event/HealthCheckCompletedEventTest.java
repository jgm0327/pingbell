package com.monit.pingbell.check.scheduler.event;

import com.monit.pingbell.check.domain.CheckResult;
import com.monit.pingbell.check.domain.CheckStatus;
import com.monit.pingbell.member.domain.Member;
import com.monit.pingbell.monitor.domain.Monitor;
import com.monit.pingbell.monitor.domain.MonitorStatus;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.RecordComponent;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class HealthCheckCompletedEventTest {

    @Test
    void fromContainsCheckResultFieldsWithoutSensitiveMonitorData() {
        LocalDateTime checkedAt = LocalDateTime.of(2026, 6, 30, 10, 0);
        UUID requestEventId = UUID.randomUUID();
        HealthCheckRequestedEvent requestEvent = new HealthCheckRequestedEvent(
                requestEventId,
                checkedAt.minusSeconds(1),
                10L,
                20L,
                1000,
                30,
                checkedAt.minusSeconds(30),
                "SCHEDULER"
        );
        CheckResult checkResult = CheckResult.builder()
                .monitor(monitor())
                .status(CheckStatus.HTTP_ERROR)
                .httpStatus(500)
                .responseTimeMs(123L)
                .errorMessage("HTTP 500")
                .build();
        ReflectionTestUtils.setField(checkResult, "id", 30L);

        HealthCheckCompletedEvent event = HealthCheckCompletedEvent.from(requestEvent, checkResult, checkedAt);

        assertThat(event.eventId()).isNotNull();
        assertThat(event.requestEventId()).isEqualTo(requestEventId);
        assertThat(event.monitorId()).isEqualTo(10L);
        assertThat(event.memberId()).isEqualTo(20L);
        assertThat(event.checkResultId()).isEqualTo(30L);
        assertThat(event.status()).isEqualTo(CheckStatus.HTTP_ERROR);
        assertThat(event.httpStatus()).isEqualTo(500);
        assertThat(event.responseTimeMs()).isEqualTo(123L);
        assertThat(event.errorMessage()).isEqualTo("HTTP 500");
        assertThat(event.checkedAt()).isEqualTo(checkedAt);
        assertThat(Arrays.stream(HealthCheckCompletedEvent.class.getRecordComponents())
                .map(RecordComponent::getName))
                .doesNotContain("url", "email", "target", "secret");
    }

    private Monitor monitor() {
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
                .nextCheckAt(LocalDateTime.of(2026, 6, 30, 9, 59))
                .build();
        ReflectionTestUtils.setField(monitor, "id", 10L);
        return monitor;
    }
}
