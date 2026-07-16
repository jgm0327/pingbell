package com.monit.pingbell.runbook.embedding;

import java.util.List;

public interface EmbeddingClient {
    EmbeddingBatch embed(List<EmbeddingInput> inputs);
}
