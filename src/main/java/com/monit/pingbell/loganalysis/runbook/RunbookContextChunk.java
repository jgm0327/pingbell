package com.monit.pingbell.loganalysis.runbook;

/**
 * A single Runbook chunk that was actually selected for inclusion in the log analysis prompt context.
 * Only chunks represented by this type are shown to the model, and only their {@code chunkId} may be
 * cited back in the model's structured response.
 */
public record RunbookContextChunk(
        String chunkId,
        String documentId,
        String title,
        int version,
        String sectionTitle,
        String content
) {
}
