package com.monit.pingbell.global.dlq;

public enum DlqDryRunStatus {
    REPROCESSABLE,
    SKIP_ALREADY_PROCESSED,
    NOT_REPROCESSABLE
}
