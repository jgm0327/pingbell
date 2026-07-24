package com.monit.pingbell.runbook.search;

import lombok.Getter;

@Getter
public class RunbookSearchException extends RuntimeException {
    private final String code;

    public RunbookSearchException(String code, String message) {
        super(message);
        this.code = code;
    }

    public RunbookSearchException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }
}
