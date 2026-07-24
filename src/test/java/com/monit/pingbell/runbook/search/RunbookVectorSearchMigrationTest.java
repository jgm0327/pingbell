package com.monit.pingbell.runbook.search;

import com.monit.pingbell.runbook.domain.RunbookDocumentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("migration")
@ActiveProfiles("migration-test")
@SpringBootTest
@Testcontainers
class RunbookVectorSearchMigrationTest {
    private static final DockerImageName PGVECTOR_IMAGE = DockerImageName.parse("pgvector/pgvector:0.8.2-pg16")
            .asCompatibleSubstituteFor("postgres");

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(PGVECTOR_IMAGE)
            .withDatabaseName("pingbell_vector_search")
            .withUsername("pingbell")
            .withPassword("synthetic-migration-password");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired JdbcRunbookVectorSearchStore store;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("delete from runbook_chunk_embeddings");
        jdbcTemplate.update("delete from runbook_chunks");
        jdbcTemplate.update("delete from runbook_revision_error_types");
        jdbcTemplate.update("delete from runbook_revisions");
        jdbcTemplate.update("delete from runbook_documents");

        seedDocument(101L, "owned", 1, "SUPERSEDED", "order-api", "TIMEOUT", "[0,1,0]", "stale");
        seedRevision(101L, "owned", 2, "ACTIVE", "order-api", "TIMEOUT", "[1,0,0]", "best", 0);
        seedChunkAndEmbedding(101L, "owned", 2, "second", 1, "[0.98,0.02,0]", "second chunk");
        seedDocument(202L, "foreign", 1, "ACTIVE", "order-api", "TIMEOUT", "[1,0,0]", "foreign");
        seedDocument(101L, "wrong-service", 1, "ACTIVE", "billing-api", "TIMEOUT", "[1,0,0]", "wrong-service");
        seedDocument(101L, "wrong-error", 1, "ACTIVE", "order-api", "HTTP_5XX", "[1,0,0]", "wrong-error");
        seedDocument(101L, "draft", 1, "DRAFT", "order-api", "TIMEOUT", "[1,0,0]", "draft");
        seedDocument(101L, "retired", 1, "RETIRED", "order-api", "TIMEOUT", "[1,0,0]", "retired");
    }

    @Test
    void tenantStatusLatestVersionAndMetadataAreFilteredBeforeScoring() {
        List<RunbookSearchCandidate> results = store.search(101L, criteria(5, 0.7),
                "fake-search-v1", new double[]{1, 0, 0});

        assertThat(results).hasSize(1);
        RunbookSearchCandidate result = results.getFirst();
        assertThat(result.tenantId()).isEqualTo(101L);
        assertThat(result.documentId()).isEqualTo("owned");
        assertThat(result.version()).isEqualTo(2);
        assertThat(result.chunkId()).isEqualTo("best");
    }

    @Test
    void lowRelevanceReturnsEmptyWithoutCrossTenantOrUnrelatedFill() {
        assertThat(store.search(101L, criteria(5, 0.9999), "fake-search-v1",
                new double[]{0, 0, 1})).isEmpty();
    }

    @Test
    void directIdentifiersRemainTenantAndActiveLatestRevisionScoped() {
        assertThat(store.findActiveChunk(101L, "owned", 2, "best")).isPresent();
        assertThat(store.findActiveChunk(202L, "owned", 2, "best")).isEmpty();
        assertThat(store.findActiveChunk(101L, "owned", 1, "stale")).isEmpty();
        assertThat(store.findActiveChunk(101L, "retired", 1, "retired")).isEmpty();
    }

    private RunbookSearchCriteria criteria(int topK, double minimumRelevance) {
        return new RunbookSearchCriteria("synthetic timeout", "order-api", List.of("TIMEOUT"),
                RunbookDocumentType.RUNBOOK, topK, minimumRelevance);
    }

    private void seedDocument(Long tenantId, String documentId, int version, String status,
                              String serviceName, String errorType, String vector, String chunkId) {
        jdbcTemplate.update("insert into runbook_documents (tenant_id, owner_id, document_id) values (?, ?, ?)",
                tenantId, tenantId, documentId);
        seedRevision(tenantId, documentId, version, status, serviceName, errorType, vector, chunkId, 0);
    }

    private void seedRevision(Long tenantId, String documentId, int version, String status,
                              String serviceName, String errorType, String vector, String chunkId, int chunkOrder) {
        Long documentPk = jdbcTemplate.queryForObject(
                "select id from runbook_documents where tenant_id = ? and document_id = ?",
                Long.class, tenantId, documentId);
        jdbcTemplate.update("""
                insert into runbook_revisions
                    (runbook_document_id, title, document_type, service_name, version, status, content, updated_at)
                values (?, ?, 'RUNBOOK', ?, ?, ?, 'synthetic', now())
                """, documentPk, "Synthetic " + documentId, serviceName, version, status);
        Long revisionPk = jdbcTemplate.queryForObject(
                "select id from runbook_revisions where runbook_document_id = ? and version = ?",
                Long.class, documentPk, version);
        jdbcTemplate.update("""
                insert into runbook_revision_error_types (runbook_revision_id, sort_order, error_type)
                values (?, 0, ?)
                """, revisionPk, errorType);
        seedChunkAndEmbedding(tenantId, documentId, version, chunkId, chunkOrder, vector,
                "content " + chunkId);
    }

    private void seedChunkAndEmbedding(Long tenantId, String documentId, int version, String chunkId,
                                       int chunkOrder, String vector, String content) {
        jdbcTemplate.update("""
                insert into runbook_chunks
                    (tenant_id, document_id, version, chunk_id, chunk_order, section_title,
                     content, content_hash, created_at)
                values (?, ?, ?, ?, ?, '확인 절차', ?, ?, now())
                """, tenantId, documentId, version, chunkId, chunkOrder, content, "a".repeat(64));
        jdbcTemplate.update("""
                insert into runbook_chunk_embeddings
                    (tenant_id, document_id, version, chunk_id, embedding, model_name,
                     embedding_dimension, generated_at, active)
                values (?, ?, ?, ?, cast(? as public.vector), 'fake-search-v1', 3, now(), true)
                """, tenantId, documentId, version, chunkId, vector);
    }
}
