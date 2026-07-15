package com.monit.pingbell.runbook.embedding;

import com.monit.pingbell.runbook.repository.RunbookRevisionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;

@Repository
@RequiredArgsConstructor
public class JdbcRunbookEmbeddingStore implements RunbookEmbeddingStore {
    private final JdbcTemplate jdbcTemplate;
    private final RunbookRevisionRepository revisionRepository;

    @Override
    @Transactional
    public void replaceActiveEmbeddings(Long tenantId, String documentId, int version,
                                        List<RunbookChunk> chunks, EmbeddingBatch batch, Instant generatedAt) {
        revisionRepository.findActiveOwnedForUpdate(tenantId, documentId, version)
                .orElseThrow(() -> new RunbookEmbeddingException("RUNBOOK_ACTIVE_REVISION_NOT_FOUND",
                        "The tenant-owned active Runbook revision was not found."));

        for (RunbookChunk chunk : chunks) {
            requireBoundary(tenantId, documentId, version, chunk.boundary());
            jdbcTemplate.update("""
                    insert into runbook_chunks
                        (tenant_id, document_id, version, chunk_id, chunk_order, section_title,
                         content, content_hash, created_at)
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    on conflict (tenant_id, document_id, version, chunk_id) do update set
                        chunk_order = excluded.chunk_order,
                        section_title = excluded.section_title,
                        content = excluded.content,
                        content_hash = excluded.content_hash
                    """, tenantId, documentId, version, chunk.chunkId(), chunk.chunkOrder(), chunk.sectionTitle(),
                    chunk.content(), chunk.contentHash(), generatedAt.atOffset(ZoneOffset.UTC));
        }

        List<EmbeddingBoundary> activeBoundaries = jdbcTemplate.query("""
                select tenant_id, document_id, version, chunk_id from runbook_chunk_embeddings
                where tenant_id = ? and document_id = ? and version = ? and active = true
                """, (resultSet, rowNumber) -> new EmbeddingBoundary(
                resultSet.getLong("tenant_id"), resultSet.getString("document_id"),
                resultSet.getInt("version"), resultSet.getString("chunk_id")),
                tenantId, documentId, version);
        for (EmbeddingBoundary boundary : activeBoundaries) {
            requireBoundary(tenantId, documentId, version, boundary);
            jdbcTemplate.update("""
                    update runbook_chunk_embeddings set active = false
                    where tenant_id = ? and document_id = ? and version = ? and chunk_id = ? and active = true
                    """, boundary.tenantId(), boundary.documentId(), boundary.version(), boundary.chunkId());
        }

        for (EmbeddingVector vector : batch.vectors()) {
            requireBoundary(tenantId, documentId, version, vector.boundary());
            jdbcTemplate.update("""
                    insert into runbook_chunk_embeddings
                        (tenant_id, document_id, version, chunk_id, embedding, model_name,
                         embedding_dimension, generated_at, active)
                    values (?, ?, ?, ?, cast(? as public.vector), ?, ?, ?, true)
                    """, tenantId, documentId, version, vector.boundary().chunkId(), toVectorLiteral(vector.values()),
                    batch.modelName(), batch.dimension(), generatedAt.atOffset(ZoneOffset.UTC));
        }
    }

    private void requireBoundary(Long tenantId, String documentId, int version, EmbeddingBoundary boundary) {
        if (!tenantId.equals(boundary.tenantId()) || !documentId.equals(boundary.documentId())
                || version != boundary.version()) {
            throw new RunbookEmbeddingException("EMBEDDING_BOUNDARY_MISMATCH",
                    "Embedding data crossed its tenant, document, or version boundary.");
        }
    }

    private String toVectorLiteral(double[] values) {
        StringBuilder literal = new StringBuilder("[");
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                literal.append(',');
            }
            literal.append(String.format(Locale.ROOT, "%.17g", values[i]));
        }
        return literal.append(']').toString();
    }
}
