package com.monit.pingbell.check.scheduler.event;

import com.monit.pingbell.check.domain.CheckStatus;
import com.monit.pingbell.incident.service.IncidentDetectionService;
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

class HealthCheckCompletedConsumerTest {

    @Test
    void consumeDelegatesToIncidentDetectionServiceWithClockNow() {
        IncidentDetectionService incidentDetectionService = mock(IncidentDetectionService.class);
        Clock clock = Clock.fixed(Instant.parse("2026-07-01T01:00:00Z"), ZoneId.of("Asia/Seoul"));
        HealthCheckCompletedConsumer consumer = new HealthCheckCompletedConsumer(incidentDetectionService, clock);
        HealthCheckCompletedEvent event = event();

        consumer.consume(event);

        verify(incidentDetectionService)
                .detectFromCompletedEvent(event, LocalDateTime.of(2026, 7, 1, 10, 0));
    }

    @Test
    void consumeRethrowsWhenIncidentDetectionFails() {
        IncidentDetectionService incidentDetectionService = mock(IncidentDetectionService.class);
        Clock clock = Clock.fixed(Instant.parse("2026-07-01T01:00:00Z"), ZoneId.of("Asia/Seoul"));
        HealthCheckCompletedConsumer consumer = new HealthCheckCompletedConsumer(incidentDetectionService, clock);
        HealthCheckCompletedEvent event = event();

        doThrow(new IllegalStateException("detect failed"))
                .when(incidentDetectionService)
                .detectFromCompletedEvent(event, LocalDateTime.of(2026, 7, 1, 10, 0));

        assertThatThrownBy(() -> consumer.consume(event))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("detect failed");
    }

    private HealthCheckCompletedEvent event() {
        return new HealthCheckCompletedEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                10L,
                20L,
                30L,
                CheckStatus.SUCCESS,
                200,
                100L,
                null,
                LocalDateTime.of(2026, 7, 1, 9, 59)
        );
    }
}
