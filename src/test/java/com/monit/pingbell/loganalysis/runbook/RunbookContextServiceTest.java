package com.monit.pingbell.loganalysis.runbook;

import com.monit.pingbell.loganalysis.config.LogAnalysisRunbookProperties;
import com.monit.pingbell.runbook.search.RunbookSearchCriteria;
import com.monit.pingbell.runbook.search.RunbookSearchException;
import com.monit.pingbell.runbook.search.RunbookSearchResult;
import com.monit.pingbell.runbook.search.RunbookVectorSearchService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RunbookContextServiceTest {

    @Mock
    private RunbookVectorSearchService searchService;

    @Test
    void returnsEmptyContextForBlankQueryWithoutSearching() {
        RunbookContextService service = new RunbookContextService(searchService, properties());

        assertThat(service.buildContext(1L, "   ")).isEmpty();
        assertThat(service.buildContext(1L, null)).isEmpty();
        verify(searchService, never()).search(any(), any());
    }

    @Test
    void returnsEmptyContextWhenDisabled() {
        LogAnalysisRunbookProperties properties = properties();
        properties.setEnabled(false);
        RunbookContextService service = new RunbookContextService(searchService, properties);

        assertThat(service.buildContext(1L, "timeout")).isEmpty();
        verify(searchService, never()).search(any(), any());
    }

    @Test
    void searchesWithAuthenticatedTenantAndConfiguredCriteria() {
        RunbookContextService service = new RunbookContextService(searchService, properties());
        when(searchService.search(eq(1L), any())).thenReturn(List.of(
                result("doc-1", "chunk-1", "content-1")
        ));

        List<RunbookContextChunk> context = service.buildContext(1L, "connection timeout");

        ArgumentCaptor<RunbookSearchCriteria> criteria = ArgumentCaptor.forClass(RunbookSearchCriteria.class);
        verify(searchService).search(eq(1L), criteria.capture());
        assertThat(criteria.getValue().query()).isEqualTo("connection timeout");
        assertThat(criteria.getValue().topK()).isEqualTo(3);
        assertThat(criteria.getValue().minimumRelevance()).isEqualTo(0.5);
        assertThat(context).hasSize(1);
        assertThat(context.getFirst().chunkId()).isEqualTo("chunk-1");
    }

    @Test
    void capsSelectedChunksByCountAndCharacterBudget() {
        LogAnalysisRunbookProperties properties = properties();
        properties.setMaxContextChunks(2);
        properties.setMaxContextCharacters(15);
        RunbookContextService service = new RunbookContextService(searchService, properties);
        when(searchService.search(any(), any())).thenReturn(List.of(
                result("doc-1", "chunk-1", "0123456789"),
                result("doc-2", "chunk-2", "0123456789"),
                result("doc-3", "chunk-3", "0123456789")
        ));

        List<RunbookContextChunk> context = service.buildContext(1L, "query");

        assertThat(context).hasSize(2);
        assertThat(context.get(0).content()).isEqualTo("0123456789");
        assertThat(context.get(1).content()).isEqualTo("01234");
    }

    @Test
    void fallsBackToEmptyContextWhenSearchFails() {
        RunbookContextService service = new RunbookContextService(searchService, properties());
        when(searchService.search(any(), any()))
                .thenThrow(new RunbookSearchException("RUNBOOK_QUERY_EMBEDDING_FAILED", "boom"));

        assertThat(service.buildContext(1L, "query")).isEmpty();
    }

    private LogAnalysisRunbookProperties properties() {
        LogAnalysisRunbookProperties properties = new LogAnalysisRunbookProperties();
        properties.setEnabled(true);
        properties.setTopK(3);
        properties.setMinimumRelevance(0.5);
        properties.setMaxContextChunks(3);
        properties.setMaxContextCharacters(6000);
        properties.setMaxQueryCharacters(4000);
        return properties;
    }

    private RunbookSearchResult result(String documentId, String chunkId, String content) {
        return new RunbookSearchResult(documentId, "Title " + documentId, 1, chunkId, "확인", content);
    }
}
