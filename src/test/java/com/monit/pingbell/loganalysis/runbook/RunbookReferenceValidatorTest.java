package com.monit.pingbell.loganalysis.runbook;

import com.monit.pingbell.loganalysis.dto.RunbookReferenceResponse;
import com.monit.pingbell.runbook.search.RunbookSearchException;
import com.monit.pingbell.runbook.search.RunbookSearchResult;
import com.monit.pingbell.runbook.search.RunbookVectorSearchService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RunbookReferenceValidatorTest {

    @Mock
    private RunbookVectorSearchService searchService;

    @Test
    void dropsChunkIdsThatWereNotPartOfTheProvidedContext() {
        RunbookReferenceValidator validator = new RunbookReferenceValidator(searchService);
        List<RunbookContextChunk> context = List.of(chunk("doc-1", "chunk-1", 2));

        List<RunbookReferenceResponse> references = validator.validate(1L, context, List.of("hallucinated-chunk"));

        assertThat(references).isEmpty();
        verify(searchService, never()).findActiveChunk(any(), any(), anyInt(), any());
    }

    @Test
    void keepsOnlyReferencesRevalidatedAsCurrentlyActive() {
        RunbookReferenceValidator validator = new RunbookReferenceValidator(searchService);
        List<RunbookContextChunk> context = List.of(chunk("doc-1", "chunk-1", 2), chunk("doc-2", "chunk-2", 5));
        when(searchService.findActiveChunk(1L, "doc-1", 2, "chunk-1"))
                .thenReturn(Optional.of(searchResult("doc-1", "chunk-1", 2)));
        when(searchService.findActiveChunk(1L, "doc-2", 5, "chunk-2")).thenReturn(Optional.empty());

        List<RunbookReferenceResponse> references = validator.validate(
                1L, context, List.of("chunk-1", "chunk-2"));

        assertThat(references).containsExactly(new RunbookReferenceResponse("doc-1", "Title doc-1", 2));
    }

    @Test
    void dedupesMultipleCitedChunksFromTheSameDocumentVersion() {
        RunbookReferenceValidator validator = new RunbookReferenceValidator(searchService);
        List<RunbookContextChunk> context = List.of(
                chunk("doc-1", "chunk-1", 2), chunk("doc-1", "chunk-1b", 2));
        when(searchService.findActiveChunk(eq(1L), eq("doc-1"), eq(2), anyString()))
                .thenReturn(Optional.of(searchResult("doc-1", "chunk-1", 2)));

        List<RunbookReferenceResponse> references = validator.validate(
                1L, context, List.of("chunk-1", "chunk-1b"));

        assertThat(references).containsExactly(new RunbookReferenceResponse("doc-1", "Title doc-1", 2));
    }

    @Test
    void treatsRevalidationFailureAsDropRatherThanPropagating() {
        RunbookReferenceValidator validator = new RunbookReferenceValidator(searchService);
        List<RunbookContextChunk> context = List.of(chunk("doc-1", "chunk-1", 2));
        when(searchService.findActiveChunk(1L, "doc-1", 2, "chunk-1"))
                .thenThrow(new RunbookSearchException("RUNBOOK_QUERY_EMBEDDING_FAILED", "boom"));

        List<RunbookReferenceResponse> references = validator.validate(1L, context, List.of("chunk-1"));

        assertThat(references).isEmpty();
    }

    @Test
    void returnsEmptyForNullOrEmptyInputsWithoutCallingSearch() {
        RunbookReferenceValidator validator = new RunbookReferenceValidator(searchService);
        List<RunbookContextChunk> context = List.of(chunk("doc-1", "chunk-1", 2));

        assertThat(validator.validate(1L, context, null)).isEmpty();
        assertThat(validator.validate(1L, context, List.of())).isEmpty();
        assertThat(validator.validate(1L, List.of(), List.of("chunk-1"))).isEmpty();
        verify(searchService, never()).findActiveChunk(any(), any(), anyInt(), any());
    }

    private RunbookContextChunk chunk(String documentId, String chunkId, int version) {
        return new RunbookContextChunk(chunkId, documentId, "Title " + documentId, version, "확인", "content");
    }

    private RunbookSearchResult searchResult(String documentId, String chunkId, int version) {
        return new RunbookSearchResult(documentId, "Title " + documentId, version, chunkId, "확인", "content");
    }
}
