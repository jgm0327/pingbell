package com.monit.pingbell.runbook.embedding;

import java.util.Arrays;

public record EmbeddingVector(EmbeddingBoundary boundary, double[] values) {
    public EmbeddingVector {
        values = Arrays.copyOf(values, values.length);
    }

    @Override
    public double[] values() {
        return Arrays.copyOf(values, values.length);
    }
}
