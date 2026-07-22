package com.monit.pingbell.runbook.search;

record RunbookSearchCandidate(
        Long tenantId,
        String documentId,
        String title,
        int version,
        String chunkId,
        String sectionTitle,
        String content,
        double relevance
) {
    RunbookSearchResult toResult() {
        return new RunbookSearchResult(documentId, title, version, chunkId, sectionTitle, content);
    }
}
