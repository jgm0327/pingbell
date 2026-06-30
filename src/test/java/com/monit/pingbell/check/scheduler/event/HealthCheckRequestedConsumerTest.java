package com.monit.pingbell.check.scheduler.event;

import com.monit.pingbell.check.service.CheckService;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class HealthCheckRequestedConsumerTest {

    @Test
    void consumeDelegatesToCheckServiceWithClockNow() {
        CheckService checkService = mock(CheckService.class);
        Clock clock = Clock.fixed(Instant.parse("2026-06-30T01:00:00Z"), ZoneId.of("Asia/Seoul"));
        HealthCheckRequestedConsumer consumer = new HealthCheckRequestedConsumer(checkService, clock);
        HealthCheckRequestedEvent event = event();

        consumer.consume(event);

        verify(checkService).handleRequestedCheck(event, LocalDateTime.of(2026, 6, 30, 10, 0));
    }

    @Test
    void consumeRethrowsWhenCheckServiceFails() {
        CheckService checkService = mock(CheckService.class);
        Clock clock = Clock.fixed(Instant.parse("2026-06-30T01:00:00Z"), ZoneId.of("Asia/Seoul"));
        HealthCheckRequestedConsumer consumer = new HealthCheckRequestedConsumer(checkService, clock);
        HealthCheckRequestedEvent event = event();

        doThrow(new IllegalStateException("failed"))
                .when(checkService)
                .handleRequestedCheck(event, LocalDateTime.of(2026, 6, 30, 10, 0));

        assertThatThrownBy(() -> consumer.consume(event))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("failed");
    }

    private HealthCheckRequestedEvent event() {
        LocalDateTime scheduledAt = LocalDateTime.of(2026, 6, 30, 9, 59);
        return new HealthCheckRequestedEvent(
                UUID.randomUUID(),
                scheduledAt,
                10L,
                20L,
                1000,
                30,
                scheduledAt,
                "SCHEDULER"
        );
    }
}
