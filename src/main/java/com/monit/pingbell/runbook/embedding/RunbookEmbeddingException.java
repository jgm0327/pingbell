package com.monit.pingbell.runbook.embedding;

import lombok.Getter;

@Getter
public class RunbookEmbeddingException extends RuntimeException {
    private final String code;

    public RunbookEmbeddingException(String code, String message) {
        super(message);
        this.code = code;
    }

    public RunbookEmbeddingException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }
}
