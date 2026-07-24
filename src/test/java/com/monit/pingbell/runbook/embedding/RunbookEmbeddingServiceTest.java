package com.monit.pingbell.runbook.embedding;

import com.monit.pingbell.runbook.domain.RunbookDocument;
import com.monit.pingbell.runbook.domain.RunbookDocumentType;
import com.monit.pingbell.runbook.domain.RunbookRevision;
import com.monit.pingbell.runbook.domain.RunbookStatus;
import com.monit.pingbell.runbook.repository.RunbookRevisionRepository;
import com.monit.pingbell.runbook.service.RunbookContentValidatorTest;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RunbookEmbeddingServiceTest {
    private static final Instant NOW = Instant.parse("2026-07-15T00:00:00Z");

    @Test
    void fakeClientStoresEveryEmbeddingWithItsFullBoundary() {
        RunbookRevisionRepository repository = activeRevisionRepository(7L, "rb-fake", 2);
        CapturingStore store = new CapturingStore();
        RunbookEmbeddingService service = service(repository, new FakeEmbeddingClient(
                FakeEmbeddingClient.Mode.SUCCESS), store);

        List<RunbookChunk> chunks = service.reindex(7L, "rb-fake", 2);

        assertThat(store.batch.vectors()).hasSameSizeAs(chunks);
        assertThat(store.generatedAt).isEqualTo(NOW);
        assertThat(store.batch.modelName()).isEqualTo("fake-embedding-v1");
        assertThat(store.batch.dimension()).isEqualTo(3);
        assertThat(store.batch.vectors()).allSatisfy(vector -> {
            assertThat(vector.boundary().tenantId()).isEqualTo(7L);
            assertThat(vector.boundary().documentId()).isEqualTo("rb-fake");
            assertThat(vector.boundary().version()).isEqualTo(2);
            assertThat(vector.values()).hasSize(3);
        });
    }

    @Test
    void timeoutLeavesExistingActiveEmbeddingsUntouched() {
        RunbookRevisionRepository repository = activeRevisionRepository(7L, "rb-timeout", 1);
        CapturingStore store = new CapturingStore();
        store.existingActiveMarker = "old-active";
        RunbookEmbeddingService service = service(repository, new FakeEmbeddingClient(
                FakeEmbeddingClient.Mode.TIMEOUT), store);

        assertThatThrownBy(() -> service.reindex(7L, "rb-timeout", 1))
                .isInstanceOf(RunbookEmbeddingException.class)
                .extracting("code").isEqualTo("EMBEDDING_CLIENT_FAILED");
        assertThat(store.calls).isZero();
        assertThat(store.existingActiveMarker).isEqualTo("old-active");
    }

    @Test
    void dimensionMismatchAndPartialResponseAreRejectedBeforeStorage() {
        RunbookRevisionRepository repository = activeRevisionRepository(7L, "rb-invalid", 1);
        CapturingStore mismatchStore = new CapturingStore();
        RunbookEmbeddingService mismatchService = service(repository,
                new FakeEmbeddingClient(FakeEmbeddingClient.Mode.DIMENSION_MISMATCH), mismatchStore);

        assertThatThrownBy(() -> mismatchService.reindex(7L, "rb-invalid", 1))
                .isInstanceOf(RunbookEmbeddingException.class)
                .extracting("code").isEqualTo("EMBEDDING_BATCH_INVALID");
        assertThat(mismatchStore.calls).isZero();

        CapturingStore partialStore = new CapturingStore();
        RunbookEmbeddingService partialService = service(repository,
                new FakeEmbeddingClient(FakeEmbeddingClient.Mode.PARTIAL_RESULT), partialStore);
        assertThatThrownBy(() -> partialService.reindex(7L, "rb-invalid", 1))
                .isInstanceOf(RunbookEmbeddingException.class)
                .extracting("code").isEqualTo("EMBEDDING_BATCH_INVALID");
        assertThat(partialStore.calls).isZero();
    }

    @Test
    void anotherTenantCannotLoadOrStoreTheRevision() {
        RunbookRevisionRepository repository = mock(RunbookRevisionRepository.class);
        when(repository.findByDocumentTenantIdAndDocumentDocumentIdAndVersion(8L, "rb-owned", 1))
                .thenReturn(Optional.empty());
        CapturingStore store = new CapturingStore();
        RunbookEmbeddingService service = service(repository,
                new FakeEmbeddingClient(FakeEmbeddingClient.Mode.SUCCESS), store);

        assertThatThrownBy(() -> service.reindex(8L, "rb-owned", 1))
                .isInstanceOf(RunbookEmbeddingException.class)
                .extracting("code").isEqualTo("RUNBOOK_ACTIVE_REVISION_NOT_FOUND");
        assertThat(store.calls).isZero();
    }

    private RunbookEmbeddingService service(RunbookRevisionRepository repository, EmbeddingClient client,
                                             RunbookEmbeddingStore store) {
        return new RunbookEmbeddingService(repository, new RunbookChunker(), client, store,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private RunbookRevisionRepository activeRevisionRepository(Long tenantId, String documentId, int version) {
        RunbookRevisionRepository repository = mock(RunbookRevisionRepository.class);
        RunbookDocument document = new RunbookDocument(tenantId, tenantId, documentId);
        RunbookRevision revision = new RunbookRevision(document, "합성 Runbook", RunbookDocumentType.RUNBOOK,
                "order-api", List.of("TIMEOUT"), version, RunbookStatus.ACTIVE,
                RunbookContentValidatorTest.validContent(), NOW);
        when(repository.findByDocumentTenantIdAndDocumentDocumentIdAndVersion(tenantId, documentId, version))
                .thenReturn(Optional.of(revision));
        return repository;
    }

    private static class CapturingStore implements RunbookEmbeddingStore {
        int calls;
        String existingActiveMarker;
        EmbeddingBatch batch;
        Instant generatedAt;

        @Override
        public void replaceActiveEmbeddings(Long tenantId, String documentId, int version,
                                            List<RunbookChunk> chunks, EmbeddingBatch batch, Instant generatedAt) {
            calls++;
            this.batch = batch;
            this.generatedAt = generatedAt;
            existingActiveMarker = "new-active";
        }
    }
}
