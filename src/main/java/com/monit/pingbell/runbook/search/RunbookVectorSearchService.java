package com.monit.pingbell.runbook.search;

import com.monit.pingbell.runbook.embedding.EmbeddingBatch;
import com.monit.pingbell.runbook.embedding.EmbeddingBoundary;
import com.monit.pingbell.runbook.embedding.EmbeddingClient;
import com.monit.pingbell.runbook.embedding.EmbeddingClientException;
import com.monit.pingbell.runbook.embedding.EmbeddingInput;
import com.monit.pingbell.runbook.embedding.EmbeddingVector;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class RunbookVectorSearchService {
    private static final String QUERY_DOCUMENT = "__runbook_search_query__";
    private static final String QUERY_CHUNK = "query";

    private final EmbeddingClient embeddingClient;
    private final RunbookVectorSearchStore searchStore;

    @Transactional(readOnly = true)
    public List<RunbookSearchResult> search(Long authenticatedTenantId, RunbookSearchCriteria criteria) {
        requireTenant(authenticatedTenantId);
        QueryEmbedding queryEmbedding = embedQuery(authenticatedTenantId, criteria.query());
        return searchStore.search(authenticatedTenantId, criteria, queryEmbedding.modelName(), queryEmbedding.values())
                .stream()
                .filter(candidate -> authenticatedTenantId.equals(candidate.tenantId()))
                .map(RunbookSearchCandidate::toResult)
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<RunbookSearchResult> findActiveChunk(Long authenticatedTenantId, String documentId,
                                                         int version, String chunkId) {
        requireTenant(authenticatedTenantId);
        return searchStore.findActiveChunk(authenticatedTenantId, documentId, version, chunkId)
                .filter(candidate -> authenticatedTenantId.equals(candidate.tenantId()))
                .map(RunbookSearchCandidate::toResult);
    }

    private QueryEmbedding embedQuery(Long tenantId, String query) {
        EmbeddingBoundary boundary = new EmbeddingBoundary(tenantId, QUERY_DOCUMENT, 1, QUERY_CHUNK);
        EmbeddingBatch batch;
        try {
            batch = embeddingClient.embed(List.of(new EmbeddingInput(boundary, query, sha256(query))));
        } catch (EmbeddingClientException e) {
            throw new RunbookSearchException("RUNBOOK_QUERY_EMBEDDING_FAILED",
                    "The Runbook search query could not be embedded.", e);
        }
        if (batch == null || batch.modelName() == null || batch.modelName().isBlank()
                || batch.dimension() < 1 || batch.vectors().size() != 1) {
            throw invalidEmbedding();
        }
        EmbeddingVector vector = batch.vectors().getFirst();
        double[] values = vector.values();
        if (!boundary.equals(vector.boundary()) || values.length != batch.dimension()) {
            throw invalidEmbedding();
        }
        for (double value : values) {
            if (!Double.isFinite(value)) {
                throw invalidEmbedding();
            }
        }
        return new QueryEmbedding(batch.modelName(), values);
    }

    private void requireTenant(Long tenantId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("authenticatedTenantId must not be null");
        }
    }

    private RunbookSearchException invalidEmbedding() {
        return new RunbookSearchException("RUNBOOK_QUERY_EMBEDDING_INVALID",
                "The embedding client returned an invalid query vector.");
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private record QueryEmbedding(String modelName, double[] values) {
    }
}
