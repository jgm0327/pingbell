package com.monit.pingbell.runbook.search;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Array;
import java.sql.PreparedStatement;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
class JdbcRunbookVectorSearchStore implements RunbookVectorSearchStore {
    private final JdbcTemplate jdbcTemplate;

    @Override
    public List<RunbookSearchCandidate> search(Long tenantId, RunbookSearchCriteria criteria,
                                               String modelName, double[] queryVector) {
        String sql = """
                with eligible as materialized (
                    select e.tenant_id, e.document_id, e.version, e.chunk_id, e.embedding,
                           r.title, c.section_title, c.content
                    from runbook_chunk_embeddings e
                    join runbook_chunks c
                      on c.tenant_id = e.tenant_id
                     and c.document_id = e.document_id
                     and c.version = e.version
                     and c.chunk_id = e.chunk_id
                    join runbook_documents d
                      on d.tenant_id = e.tenant_id
                     and d.document_id = e.document_id
                    join runbook_revisions r
                      on r.runbook_document_id = d.id
                     and r.version = e.version
                    where e.tenant_id = ?
                      and e.active = true
                      and e.model_name = ?
                      and e.embedding_dimension = ?
                      and r.status = 'ACTIVE'
                      and r.version = (
                          select max(latest.version)
                          from runbook_revisions latest
                          where latest.runbook_document_id = d.id
                            and latest.status = 'ACTIVE'
                      )
                      and (? is null or r.service_name = ?)
                      and (? is null or r.document_type = ?)
                      and (
                          cardinality(cast(? as varchar[])) = 0
                          or exists (
                              select 1
                              from runbook_revision_error_types error_type
                              where error_type.runbook_revision_id = r.id
                                and error_type.error_type = any(cast(? as varchar[]))
                          )
                      )
                ), scored as (
                    select eligible.*,
                           1 - (eligible.embedding <=> cast(? as public.vector)) as relevance
                    from eligible
                ), qualified as (
                    select scored.*,
                           row_number() over (
                               partition by scored.document_id
                               order by scored.relevance desc, scored.chunk_id
                           ) as document_rank
                    from scored
                    where scored.relevance >= ?
                )
                select tenant_id, document_id, title, version, chunk_id, section_title, content, relevance
                from qualified
                where document_rank = 1
                order by relevance desc, document_id
                limit ?
                """;
        String vectorLiteral = toVectorLiteral(queryVector);
        return jdbcTemplate.query(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql);
            Array errorTypes = connection.createArrayOf("varchar", criteria.errorTypes().toArray());
            int index = 1;
            statement.setLong(index++, tenantId);
            statement.setString(index++, modelName);
            statement.setInt(index++, queryVector.length);
            statement.setString(index++, criteria.serviceName());
            statement.setString(index++, criteria.serviceName());
            statement.setString(index++, criteria.documentType() == null ? null : criteria.documentType().name());
            statement.setString(index++, criteria.documentType() == null ? null : criteria.documentType().name());
            statement.setArray(index++, errorTypes);
            statement.setArray(index++, errorTypes);
            statement.setString(index++, vectorLiteral);
            statement.setDouble(index++, criteria.minimumRelevance());
            statement.setInt(index, criteria.topK());
            return statement;
        }, (resultSet, rowNumber) -> candidate(resultSet));
    }

    @Override
    public Optional<RunbookSearchCandidate> findActiveChunk(Long tenantId, String documentId,
                                                             int version, String chunkId) {
        List<RunbookSearchCandidate> matches = jdbcTemplate.query("""
                select e.tenant_id, e.document_id, r.title, e.version, e.chunk_id,
                       c.section_title, c.content, 1.0 as relevance
                from runbook_chunk_embeddings e
                join runbook_chunks c
                  on c.tenant_id = e.tenant_id
                 and c.document_id = e.document_id
                 and c.version = e.version
                 and c.chunk_id = e.chunk_id
                join runbook_documents d
                  on d.tenant_id = e.tenant_id
                 and d.document_id = e.document_id
                join runbook_revisions r
                  on r.runbook_document_id = d.id
                 and r.version = e.version
                where e.tenant_id = ? and e.document_id = ? and e.version = ? and e.chunk_id = ?
                  and e.active = true and r.status = 'ACTIVE'
                  and r.version = (
                      select max(latest.version) from runbook_revisions latest
                      where latest.runbook_document_id = d.id and latest.status = 'ACTIVE'
                  )
                """, (resultSet, rowNumber) -> candidate(resultSet), tenantId, documentId, version, chunkId);
        return matches.stream().findFirst();
    }

    private RunbookSearchCandidate candidate(java.sql.ResultSet resultSet) throws java.sql.SQLException {
        return new RunbookSearchCandidate(resultSet.getLong("tenant_id"), resultSet.getString("document_id"),
                resultSet.getString("title"), resultSet.getInt("version"), resultSet.getString("chunk_id"),
                resultSet.getString("section_title"), resultSet.getString("content"),
                resultSet.getDouble("relevance"));
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
