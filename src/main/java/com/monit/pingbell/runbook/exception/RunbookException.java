package com.monit.pingbell.runbook.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class RunbookException extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    public RunbookException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }
}
