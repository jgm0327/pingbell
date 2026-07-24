package com.monit.pingbell.runbook.embedding;

import java.util.Objects;

public record EmbeddingBoundary(Long tenantId, String documentId, int version, String chunkId) {
    public EmbeddingBoundary {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(documentId, "documentId must not be null");
        Objects.requireNonNull(chunkId, "chunkId must not be null");
        if (version < 1) {
            throw new IllegalArgumentException("version must be positive");
        }
    }
}
