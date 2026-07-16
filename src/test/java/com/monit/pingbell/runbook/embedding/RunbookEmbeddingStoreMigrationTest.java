package com.monit.pingbell.runbook.embedding;

import com.monit.pingbell.runbook.domain.RunbookDocument;
import com.monit.pingbell.runbook.domain.RunbookDocumentType;
import com.monit.pingbell.runbook.domain.RunbookRevision;
import com.monit.pingbell.runbook.domain.RunbookStatus;
import com.monit.pingbell.runbook.repository.RunbookDocumentRepository;
import com.monit.pingbell.runbook.repository.RunbookRevisionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("migration")
@ActiveProfiles("migration-test")
@SpringBootTest
@Testcontainers
class RunbookEmbeddingStoreMigrationTest {
    private static final DockerImageName PGVECTOR_IMAGE =
            DockerImageName.parse("pgvector/pgvector:0.8.2-pg16")
                    .asCompatibleSubstituteFor("postgres");
    private static final Instant GENERATED_AT = Instant.parse("2026-07-15T00:00:00Z");

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(PGVECTOR_IMAGE)
            .withDatabaseName("pingbell_embedding")
            .withUsername("pingbell")
            .withPassword("synthetic-migration-password");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired JdbcRunbookEmbeddingStore store;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired RunbookDocumentRepository documentRepository;
    @Autowired RunbookRevisionRepository revisionRepository;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("delete from runbook_chunk_embeddings");
        jdbcTemplate.update("delete from runbook_chunks");
        revisionRepository.deleteAll();
        documentRepository.deleteAll();
        RunbookDocument document = documentRepository.saveAndFlush(new RunbookDocument(101L, 101L, "rb-storage"));
        revisionRepository.saveAndFlush(new RunbookRevision(document, "합성 저장 테스트", RunbookDocumentType.RUNBOOK,
                "order-api", List.of("TIMEOUT"), 1, RunbookStatus.ACTIVE, "synthetic", GENERATED_AT));
    }

    @Test
    void successfulReindexAtomicallyReplacesActiveEmbeddings() {
        List<RunbookChunk> chunks = chunks();
        store.replaceActiveEmbeddings(101L, "rb-storage", 1, chunks,
                batch("fake-old", chunks, 0.1), GENERATED_AT);
        store.replaceActiveEmbeddings(101L, "rb-storage", 1, chunks,
                batch("fake-new", chunks, 0.4), GENERATED_AT.plusSeconds(1));

        assertThat(count("active = true and model_name = 'fake-new' and tenant_id = 101 " +
                "and document_id = 'rb-storage' and version = 1")).isEqualTo(2);
        assertThat(count("active = false and model_name = 'fake-old' and tenant_id = 101 " +
                "and document_id = 'rb-storage' and version = 1")).isEqualTo(2);
        assertThat(jdbcTemplate.queryForList("""
                select chunk_id from runbook_chunk_embeddings
                where tenant_id = 101 and document_id = 'rb-storage' and version = 1 and active = true
                order by chunk_id
                """, String.class)).containsExactly("chunk-a", "chunk-b");
    }

    @Test
    void databaseFailureRollsBackDeactivationAndPartialInsert() {
        List<RunbookChunk> chunks = chunks();
        store.replaceActiveEmbeddings(101L, "rb-storage", 1, chunks,
                batch("fake-old", chunks, 0.1), GENERATED_AT);
        EmbeddingBatch invalid = new EmbeddingBatch("fake-invalid", 3, List.of(
                new EmbeddingVector(chunks.get(0).boundary(), new double[]{0.2, 0.3, 0.4}),
                new EmbeddingVector(chunks.get(1).boundary(), new double[]{0.2, 0.3})
        ));

        assertThatThrownBy(() -> store.replaceActiveEmbeddings(101L, "rb-storage", 1, chunks,
                invalid, GENERATED_AT.plusSeconds(1)))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(count("active = true and model_name = 'fake-old' and tenant_id = 101 " +
                "and document_id = 'rb-storage' and version = 1")).isEqualTo(2);
        assertThat(count("model_name = 'fake-invalid'")).isZero();
    }

    @Test
    void anotherTenantCannotReplaceTheActiveRevisionEmbeddings() {
        List<RunbookChunk> chunks = chunks();
        store.replaceActiveEmbeddings(101L, "rb-storage", 1, chunks,
                batch("fake-old", chunks, 0.1), GENERATED_AT);

        assertThatThrownBy(() -> store.replaceActiveEmbeddings(202L, "rb-storage", 1,
                chunks.stream().map(chunk -> new RunbookChunk(202L, chunk.documentId(), chunk.version(),
                        chunk.chunkId(), chunk.chunkOrder(), chunk.sectionTitle(), chunk.content(),
                        chunk.contentHash())).toList(),
                new EmbeddingBatch("fake-foreign", 3, chunks.stream()
                        .map(chunk -> new EmbeddingVector(new EmbeddingBoundary(
                                202L, chunk.documentId(), chunk.version(), chunk.chunkId()),
                                new double[]{0.7, 0.8, 0.9})).toList()), GENERATED_AT.plusSeconds(1)))
                .isInstanceOf(RunbookEmbeddingException.class)
                .extracting("code").isEqualTo("RUNBOOK_ACTIVE_REVISION_NOT_FOUND");

        assertThat(count("active = true and model_name = 'fake-old' and tenant_id = 101 " +
                "and document_id = 'rb-storage' and version = 1")).isEqualTo(2);
        assertThat(count("tenant_id = 202")).isZero();
    }

    private List<RunbookChunk> chunks() {
        return List.of(
                new RunbookChunk(101L, "rb-storage", 1, "chunk-a", 0, "증상",
                        "## 증상\n\nsynthetic symptom", "a".repeat(64)),
                new RunbookChunk(101L, "rb-storage", 1, "chunk-b", 1, "확인 절차",
                        "## 확인 절차\n\nsynthetic check", "b".repeat(64))
        );
    }

    private EmbeddingBatch batch(String model, List<RunbookChunk> chunks, double seed) {
        return new EmbeddingBatch(model, 3, chunks.stream()
                .map(chunk -> new EmbeddingVector(chunk.boundary(), new double[]{seed, seed + 0.1, seed + 0.2}))
                .toList());
    }

    private int count(String where) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from runbook_chunk_embeddings where " + where, Integer.class);
        return count == null ? 0 : count;
    }
}
