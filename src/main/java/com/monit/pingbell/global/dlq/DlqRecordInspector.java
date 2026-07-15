package com.monit.pingbell.global.dlq;

import com.monit.pingbell.check.scheduler.event.HealthCheckCompletedEvent;
import com.monit.pingbell.check.scheduler.event.HealthCheckRequestedEvent;
import com.monit.pingbell.notification.event.NotificationRequestedEvent;
import org.springframework.stereotype.Component;

@Component
public class DlqRecordInspector {

    public String payloadType(Object payload) {
        return payload.getClass().getSimpleName();
    }

    public String primaryIds(Object payload) {
        if (payload instanceof HealthCheckRequestedEvent event) {
            return "eventId=%s monitorId=%s memberId=%s".formatted(
                    event.eventId(),
                    event.monitorId(),
                    event.memberId()
            );
        }
        if (payload instanceof HealthCheckCompletedEvent event) {
            return "eventId=%s requestEventId=%s monitorId=%s memberId=%s checkResultId=%s".formatted(
                    event.eventId(),
                    event.requestEventId(),
                    event.monitorId(),
                    event.memberId(),
                    event.checkResultId()
            );
        }
        if (payload instanceof NotificationRequestedEvent event) {
            return "eventId=%s incidentId=%s monitorId=%s memberId=%s notificationType=%s".formatted(
                    event.eventId(),
                    event.incidentId(),
                    event.monitorId(),
                    event.memberId(),
                    event.notificationType()
            );
        }
        return "";
    }
}
