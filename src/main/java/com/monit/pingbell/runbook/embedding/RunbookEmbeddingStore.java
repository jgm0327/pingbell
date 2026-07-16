package com.monit.pingbell.runbook.embedding;

import java.time.Instant;
import java.util.List;

public interface RunbookEmbeddingStore {
    void replaceActiveEmbeddings(Long tenantId, String documentId, int version,
                                 List<RunbookChunk> chunks, EmbeddingBatch batch, Instant generatedAt);
}
