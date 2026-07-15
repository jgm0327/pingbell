package com.monit.pingbell.global.dlq;

public record DlqRecordSnapshot(
        String topic,
        int partition,
        long offset,
        String payloadType,
        String primaryIds,
        Object payload,
        String readError
) {

    public static DlqRecordSnapshot readable(
            String topic,
            int partition,
            long offset,
            String payloadType,
            String primaryIds,
            Object payload
    ) {
        return new DlqRecordSnapshot(topic, partition, offset, payloadType, primaryIds, payload, null);
    }

    public static DlqRecordSnapshot unreadable(
            String topic,
            int partition,
            long offset,
            String payloadType,
            String readError
    ) {
        return new DlqRecordSnapshot(topic, partition, offset, payloadType, "", null, readError);
    }

    public boolean isReadable() {
        return payload != null;
    }
}
