package com.monit.pingbell.runbook.dto;

/**
 * Result of manually triggering {@code RunbookEmbeddingService.reindex} for a single ACTIVE revision.
 * This is a development/testing convenience: the normal create/activate flow does not yet trigger
 * embedding indexing automatically.
 */
public record RunbookReindexResponse(int chunkCount) {
}
