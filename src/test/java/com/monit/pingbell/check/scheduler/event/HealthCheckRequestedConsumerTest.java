package com.monit.pingbell.check.scheduler.event;

import com.monit.pingbell.check.service.CheckService;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class HealthCheckRequestedConsumerTest {

    @Test
    void consumeDelegatesToCheckServiceWithClockNow() {
        CheckService checkService = mock(CheckService.class);
        HealthCheckCompletedProducer completedProducer = mock(HealthCheckCompletedProducer.class);
        Clock clock = Clock.fixed(Instant.parse("2026-06-30T01:00:00Z"), ZoneId.of("Asia/Seoul"));
        HealthCheckRequestedConsumer consumer = new HealthCheckRequestedConsumer(checkService, completedProducer, clock);
        HealthCheckRequestedEvent event = event();
        org.mockito.Mockito.when(checkService.handleRequestedCheck(event, LocalDateTime.of(2026, 6, 30, 10, 0)))
                .thenReturn(Optional.empty());

        consumer.consume(event);

        verify(checkService).handleRequestedCheck(event, LocalDateTime.of(2026, 6, 30, 10, 0));
    }

    @Test
    void consumePublishesCompletedEventWhenCheckServiceReturnsEvent() {
        CheckService checkService = mock(CheckService.class);
        HealthCheckCompletedProducer completedProducer = mock(HealthCheckCompletedProducer.class);
        Clock clock = Clock.fixed(Instant.parse("2026-06-30T01:00:00Z"), ZoneId.of("Asia/Seoul"));
        HealthCheckRequestedConsumer consumer = new HealthCheckRequestedConsumer(checkService, completedProducer, clock);
        HealthCheckRequestedEvent event = event();
        HealthCheckCompletedEvent completedEvent = completedEvent(event.eventId());

        org.mockito.Mockito.when(checkService.handleRequestedCheck(event, LocalDateTime.of(2026, 6, 30, 10, 0)))
                .thenReturn(Optional.of(completedEvent));

        consumer.consume(event);

        verify(completedProducer).publish(completedEvent);
    }

    @Test
    void consumeRethrowsWhenCheckServiceFails() {
        CheckService checkService = mock(CheckService.class);
        HealthCheckCompletedProducer completedProducer = mock(HealthCheckCompletedProducer.class);
        Clock clock = Clock.fixed(Instant.parse("2026-06-30T01:00:00Z"), ZoneId.of("Asia/Seoul"));
        HealthCheckRequestedConsumer consumer = new HealthCheckRequestedConsumer(checkService, completedProducer, clock);
        HealthCheckRequestedEvent event = event();

        doThrow(new IllegalStateException("failed"))
                .when(checkService)
                .handleRequestedCheck(event, LocalDateTime.of(2026, 6, 30, 10, 0));

        assertThatThrownBy(() -> consumer.consume(event))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("failed");
    }

    @Test
    void consumeRethrowsWhenCompletedPublishFails() {
        CheckService checkService = mock(CheckService.class);
        HealthCheckCompletedProducer completedProducer = mock(HealthCheckCompletedProducer.class);
        Clock clock = Clock.fixed(Instant.parse("2026-06-30T01:00:00Z"), ZoneId.of("Asia/Seoul"));
        HealthCheckRequestedConsumer consumer = new HealthCheckRequestedConsumer(checkService, completedProducer, clock);
        HealthCheckRequestedEvent event = event();
        HealthCheckCompletedEvent completedEvent = completedEvent(event.eventId());

        org.mockito.Mockito.when(checkService.handleRequestedCheck(event, LocalDateTime.of(2026, 6, 30, 10, 0)))
                .thenReturn(Optional.of(completedEvent));
        doThrow(new IllegalStateException("publish failed"))
                .when(completedProducer)
                .publish(completedEvent);

        assertThatThrownBy(() -> consumer.consume(event))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("publish failed");
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

    private HealthCheckCompletedEvent completedEvent(UUID requestEventId) {
        return new HealthCheckCompletedEvent(
                UUID.randomUUID(),
                requestEventId,
                10L,
                20L,
                30L,
                com.monit.pingbell.check.domain.CheckStatus.SUCCESS,
                200,
                100L,
                null,
                LocalDateTime.of(2026, 6, 30, 10, 0)
        );
    }
}
