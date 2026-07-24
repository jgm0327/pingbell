package com.monit.pingbell.runbook.search;

public record RunbookSearchResult(
        String documentId,
        String title,
        int version,
        String chunkId,
        String sectionTitle,
        String content
) {
}
