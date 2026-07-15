package com.monit.pingbell.runbook.embedding;

import java.util.List;

final class FakeEmbeddingClient implements EmbeddingClient {
    enum Mode { SUCCESS, TIMEOUT, DIMENSION_MISMATCH, PARTIAL_RESULT }

    private final Mode mode;

    FakeEmbeddingClient(Mode mode) {
        this.mode = mode;
    }

    @Override
    public EmbeddingBatch embed(List<EmbeddingInput> inputs) {
        if (mode == Mode.TIMEOUT) {
            throw new EmbeddingClientException("Synthetic embedding timeout");
        }
        List<EmbeddingInput> selected = mode == Mode.PARTIAL_RESULT
                ? inputs.subList(0, Math.max(0, inputs.size() - 1))
                : inputs;
        List<EmbeddingVector> vectors = selected.stream()
                .map(input -> new EmbeddingVector(input.boundary(), values(input)))
                .toList();
        return new EmbeddingBatch("fake-embedding-v1", 3, vectors);
    }

    private double[] values(EmbeddingInput input) {
        int seed = input.contentHash().hashCode();
        if (mode == Mode.DIMENSION_MISMATCH) {
            return new double[]{seed / (double) Integer.MAX_VALUE, 0.5};
        }
        return new double[]{seed / (double) Integer.MAX_VALUE, input.content().length() / 10_000.0, 1.0};
    }
}
