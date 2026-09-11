package com.monit.pingbell.loganalysis.runbook;

import com.monit.pingbell.loganalysis.config.LogAnalysisRunbookProperties;
import com.monit.pingbell.runbook.search.RunbookSearchCriteria;
import com.monit.pingbell.runbook.search.RunbookSearchResult;
import com.monit.pingbell.runbook.search.RunbookVectorSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the Runbook context that is actually shown to the log analysis prompt.
 * <p>
 * The Tenant boundary, ACTIVE/latest-version filter and relevance threshold are already enforced by
 * {@link RunbookVectorSearchService}; this component only selects the top candidates within a context
 * size budget and turns a failed or empty search into an empty context so the caller can safely fall
 * back to non-RAG log analysis.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RunbookContextService {

    private final RunbookVectorSearchService searchService;
    private final LogAnalysisRunbookProperties properties;

    public List<RunbookContextChunk> buildContext(Long tenantId, String rawQuery) {
        if (!properties.isEnabled() || tenantId == null) {
            return List.of();
        }
        String query = normalizeQuery(rawQuery);
        if (query == null) {
            return List.of();
        }
        try {
            RunbookSearchCriteria criteria = new RunbookSearchCriteria(
                    query, null, List.of(), null, properties.getTopK(), properties.getMinimumRelevance());
            List<RunbookSearchResult> results = searchService.search(tenantId, criteria);
            return capToBudget(results);
        } catch (RuntimeException e) {
            log.warn("Runbook context search failed for tenant {}; continuing without RAG context.", tenantId, e);
            return List.of();
        }
    }

    private String normalizeQuery(String rawQuery) {
        if (rawQuery == null) {
            return null;
        }
        String trimmed = rawQuery.strip();
        if (trimmed.isEmpty()) {
            return null;
        }
        int max = properties.getMaxQueryCharacters();
        return trimmed.length() > max ? trimmed.substring(0, max) : trimmed;
    }

    private List<RunbookContextChunk> capToBudget(List<RunbookSearchResult> results) {
        List<RunbookContextChunk> chunks = new ArrayList<>();
        int remainingCharacters = properties.getMaxContextCharacters();
        for (RunbookSearchResult result : results) {
            if (chunks.size() >= properties.getMaxContextChunks() || remainingCharacters <= 0) {
                break;
            }
            String content = result.content() == null ? "" : result.content();
            if (content.length() > remainingCharacters) {
                content = content.substring(0, remainingCharacters);
            }
            chunks.add(new RunbookContextChunk(
                    result.chunkId(), result.documentId(), result.title(), result.version(),
                    result.sectionTitle(), content));
            remainingCharacters -= content.length();
        }
        return List.copyOf(chunks);
    }
}
