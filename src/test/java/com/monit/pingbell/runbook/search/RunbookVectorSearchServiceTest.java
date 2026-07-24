package com.monit.pingbell.runbook.search;

import com.monit.pingbell.runbook.domain.RunbookDocumentType;
import com.monit.pingbell.runbook.embedding.EmbeddingBatch;
import com.monit.pingbell.runbook.embedding.EmbeddingBoundary;
import com.monit.pingbell.runbook.embedding.EmbeddingClient;
import com.monit.pingbell.runbook.embedding.EmbeddingVector;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RunbookVectorSearchServiceTest {

    @Test
    void queryEmbeddingAndEveryCandidateUseTheAuthenticatedTenant() {
        CapturingClient client = new CapturingClient();
        CapturingStore store = new CapturingStore();
        RunbookVectorSearchService service = new RunbookVectorSearchService(client, store);
        RunbookSearchCriteria criteria = criteria(3, 0.7);

        List<RunbookSearchResult> results = service.search(7L, criteria);

        assertThat(client.tenantId).isEqualTo(7L);
        assertThat(store.searchTenantId).isEqualTo(7L);
        assertThat(store.criteria).isSameAs(criteria);
        assertThat(results).extracting(RunbookSearchResult::documentId).containsExactly("owned");
    }

    @Test
    void resultRevalidationDropsForeignCandidatesInsteadOfFillingTopK() {
        CapturingStore store = new CapturingStore();
        store.includeForeignCandidate = true;
        RunbookVectorSearchService service = new RunbookVectorSearchService(new CapturingClient(), store);

        List<RunbookSearchResult> results = service.search(7L, criteria(5, 0.6));

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().documentId()).isEqualTo("owned");
    }

    @Test
    void directChunkLookupRepeatsTenantBoundaryAndDoesNotFallback() {
        CapturingStore store = new CapturingStore();
        RunbookVectorSearchService service = new RunbookVectorSearchService(new CapturingClient(), store);

        Optional<RunbookSearchResult> missing = service.findActiveChunk(8L, "owned", 2, "chunk-owned");

        assertThat(store.directTenantId).isEqualTo(8L);
        assertThat(missing).isEmpty();
    }

    @Test
    void criteriaRejectsUnboundedTopKAndInvalidMinimumRelevance() {
        assertThatThrownBy(() -> criteria(RunbookSearchCriteria.MAX_TOP_K + 1, 0.5))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> criteria(3, -0.1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> criteria(3, 1.1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private RunbookSearchCriteria criteria(int topK, double minimumRelevance) {
        return new RunbookSearchCriteria("synthetic timeout", "order-api", List.of("TIMEOUT"),
                RunbookDocumentType.RUNBOOK, topK, minimumRelevance);
    }

    private static class CapturingClient implements EmbeddingClient {
        Long tenantId;

        @Override
        public EmbeddingBatch embed(List<com.monit.pingbell.runbook.embedding.EmbeddingInput> inputs) {
            EmbeddingBoundary boundary = inputs.getFirst().boundary();
            tenantId = boundary.tenantId();
            return new EmbeddingBatch("fake-search-v1", 3,
                    List.of(new EmbeddingVector(boundary, new double[]{1.0, 0.0, 0.0})));
        }
    }

    private static class CapturingStore implements RunbookVectorSearchStore {
        Long searchTenantId;
        Long directTenantId;
        RunbookSearchCriteria criteria;
        boolean includeForeignCandidate;

        @Override
        public List<RunbookSearchCandidate> search(Long tenantId, RunbookSearchCriteria criteria,
                                                   String modelName, double[] queryVector) {
            this.searchTenantId = tenantId;
            this.criteria = criteria;
            RunbookSearchCandidate owned = candidate(tenantId, "owned", "chunk-owned");
            return includeForeignCandidate
                    ? List.of(owned, candidate(999L, "foreign", "chunk-foreign"))
                    : List.of(owned);
        }

        @Override
        public Optional<RunbookSearchCandidate> findActiveChunk(Long tenantId, String documentId,
                                                                 int version, String chunkId) {
            directTenantId = tenantId;
            if (tenantId == 7L && documentId.equals("owned") && version == 2 && chunkId.equals("chunk-owned")) {
                return Optional.of(candidate(7L, "owned", "chunk-owned"));
            }
            return Optional.empty();
        }

        private RunbookSearchCandidate candidate(Long tenantId, String documentId, String chunkId) {
            return new RunbookSearchCandidate(tenantId, documentId, "Synthetic", 2, chunkId,
                    "확인 절차", "synthetic content", 0.9);
        }
    }
}
