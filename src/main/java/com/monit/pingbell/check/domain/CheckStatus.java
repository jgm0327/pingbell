package com.monit.pingbell.check.domain;

public enum CheckStatus {
    SUCCESS,
    FAILURE,
    TIMEOUT,
    HTTP_ERROR,
    SLOW_RESPONSE
}
