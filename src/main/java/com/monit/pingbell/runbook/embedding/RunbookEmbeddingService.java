package com.monit.pingbell.runbook.embedding;

import com.monit.pingbell.runbook.domain.RunbookRevision;
import com.monit.pingbell.runbook.domain.RunbookStatus;
import com.monit.pingbell.runbook.repository.RunbookRevisionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class RunbookEmbeddingService {
    private final RunbookRevisionRepository revisionRepository;
    private final RunbookChunker chunker;
    private final EmbeddingClient embeddingClient;
    private final RunbookEmbeddingStore embeddingStore;
    private final Clock clock;

    public List<RunbookChunk> reindex(Long tenantId, String documentId, int version) {
        RevisionContent revision = loadActiveRevision(tenantId, documentId, version);
        List<RunbookChunk> chunks = chunker.chunk(tenantId, documentId, version, revision.content());
        List<EmbeddingInput> inputs = chunks.stream()
                .map(chunk -> new EmbeddingInput(chunk.boundary(), chunk.content(), chunk.contentHash()))
                .toList();

        EmbeddingBatch batch;
        try {
            batch = embeddingClient.embed(inputs);
        } catch (EmbeddingClientException e) {
            throw new RunbookEmbeddingException("EMBEDDING_CLIENT_FAILED",
                    "The embedding client failed before any active embedding was changed.", e);
        }
        EmbeddingBatch orderedBatch = validateAndOrder(inputs, batch);
        embeddingStore.replaceActiveEmbeddings(tenantId, documentId, version, chunks, orderedBatch,
                Instant.now(clock));
        return chunks;
    }

    protected RevisionContent loadActiveRevision(Long tenantId, String documentId, int version) {
        RunbookRevision revision = revisionRepository
                .findByDocumentTenantIdAndDocumentDocumentIdAndVersion(tenantId, documentId, version)
                .filter(candidate -> candidate.getStatus() == RunbookStatus.ACTIVE)
                .orElseThrow(() -> new RunbookEmbeddingException("RUNBOOK_ACTIVE_REVISION_NOT_FOUND",
                        "The tenant-owned active Runbook revision was not found."));
        return new RevisionContent(revision.getContent());
    }

    private EmbeddingBatch validateAndOrder(List<EmbeddingInput> inputs, EmbeddingBatch batch) {
        if (batch == null || batch.modelName() == null || batch.modelName().isBlank() || batch.dimension() < 1) {
            throw invalidBatch("Embedding model metadata is invalid.");
        }
        Map<EmbeddingBoundary, EmbeddingVector> byBoundary = new HashMap<>();
        for (EmbeddingVector vector : batch.vectors()) {
            if (vector == null || vector.boundary() == null || vector.values() == null
                    || vector.values().length != batch.dimension()) {
                throw invalidBatch("Embedding vector dimension does not match its metadata.");
            }
            for (double value : vector.values()) {
                if (!Double.isFinite(value)) {
                    throw invalidBatch("Embedding vector contains a non-finite value.");
                }
            }
            if (byBoundary.put(vector.boundary(), vector) != null) {
                throw invalidBatch("Embedding client returned a duplicate chunk boundary.");
            }
        }
        Set<EmbeddingBoundary> expected = inputs.stream().map(EmbeddingInput::boundary)
                .collect(java.util.stream.Collectors.toCollection(HashSet::new));
        if (!byBoundary.keySet().equals(expected)) {
            throw invalidBatch("Embedding client returned missing or foreign chunk boundaries.");
        }
        List<EmbeddingVector> ordered = inputs.stream().map(input -> byBoundary.get(input.boundary())).toList();
        return new EmbeddingBatch(batch.modelName(), batch.dimension(), ordered);
    }

    private RunbookEmbeddingException invalidBatch(String message) {
        return new RunbookEmbeddingException("EMBEDDING_BATCH_INVALID", message);
    }

    protected record RevisionContent(String content) {
    }
}
