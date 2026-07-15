package com.monit.pingbell.runbook.embedding;

public record RunbookChunk(
        Long tenantId,
        String documentId,
        int version,
        String chunkId,
        int chunkOrder,
        String sectionTitle,
        String content,
        String contentHash
) {
    public EmbeddingBoundary boundary() {
        return new EmbeddingBoundary(tenantId, documentId, version, chunkId);
    }
}
