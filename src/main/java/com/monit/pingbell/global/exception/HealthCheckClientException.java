package com.monit.pingbell.global.exception;

public class HealthCheckClientException extends RuntimeException {
    public HealthCheckClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
