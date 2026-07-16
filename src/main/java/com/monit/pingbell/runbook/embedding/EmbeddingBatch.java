package com.monit.pingbell.runbook.embedding;

import java.util.List;

public record EmbeddingBatch(String modelName, int dimension, List<EmbeddingVector> vectors) {
    public EmbeddingBatch {
        vectors = List.copyOf(vectors);
    }
}
