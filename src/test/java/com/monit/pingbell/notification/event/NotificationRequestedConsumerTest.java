package com.monit.pingbell.notification.event;

import com.monit.pingbell.notification.service.NotificationWorkerService;
import com.monit.pingbell.notification.type.NotificationType;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class NotificationRequestedConsumerTest {

    @Test
    void consumeDelegatesToWorkerService() {
        NotificationWorkerService workerService = mock(NotificationWorkerService.class);
        NotificationRequestedConsumer consumer = new NotificationRequestedConsumer(workerService);
        NotificationRequestedEvent event = event();

        consumer.consume(event);

        verify(workerService).handle(event);
    }

    @Test
    void consumeRethrowsWhenWorkerFails() {
        NotificationWorkerService workerService = mock(NotificationWorkerService.class);
        NotificationRequestedConsumer consumer = new NotificationRequestedConsumer(workerService);
        NotificationRequestedEvent event = event();

        doThrow(new IllegalStateException("notification failed"))
                .when(workerService)
                .handle(event);

        assertThatThrownBy(() -> consumer.consume(event))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("notification failed");
    }

    private NotificationRequestedEvent event() {
        return new NotificationRequestedEvent(
                UUID.randomUUID(),
                10L,
                20L,
                30L,
                NotificationType.INCIDENT_OPEN,
                LocalDateTime.of(2026, 7, 1, 10, 0)
        );
    }
}
