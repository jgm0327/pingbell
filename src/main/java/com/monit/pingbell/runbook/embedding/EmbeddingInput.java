package com.monit.pingbell.runbook.embedding;

public record EmbeddingInput(EmbeddingBoundary boundary, String content, String contentHash) {
}
