package com.monit.pingbell.runbook.service;

import com.monit.pingbell.runbook.domain.*;
import com.monit.pingbell.runbook.dto.*;
import com.monit.pingbell.runbook.exception.RunbookException;
import com.monit.pingbell.runbook.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import java.util.List;
import java.util.concurrent.Executors;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class RunbookServiceTest {
    @Autowired RunbookService service;
    @Autowired RunbookRevisionRepository revisionRepository;
    @Autowired RunbookDocumentRepository documentRepository;

    @BeforeEach
    void cleanUp() {
        revisionRepository.deleteAll();
        documentRepository.deleteAll();
    }

    @Test
    void isolatesDirectAccessByTenant() {
        service.create(1L, createRequest("tenant-boundary", RunbookStatus.ACTIVE));

        assertThatThrownBy(() -> service.getRevision(2L, "tenant-boundary", 1))
                .isInstanceOf(RunbookException.class)
                .extracting("code").isEqualTo("RUNBOOK_NOT_FOUND");
    }

    @Test
    void activatingNewVersionSupersedesPreviousVersionAtomically() {
        service.create(1L, createRequest("versioned", RunbookStatus.ACTIVE));
        RunbookResponse second = service.createVersion(1L, "versioned", revisionRequest(RunbookStatus.ACTIVE));

        assertThat(second.version()).isEqualTo(2);
        assertThat(service.getRevision(1L, "versioned", 1).status()).isEqualTo(RunbookStatus.SUPERSEDED);
        assertThat(service.getActiveRunbooks(1L)).extracting(RunbookResponse::version).containsExactly(2);
    }

    @Test
    void rejectsActivatingAnOlderDraftAndKeepsCurrentActive() {
        service.create(1L, createRequest("rollback", RunbookStatus.DRAFT));
        service.createVersion(1L, "rollback", revisionRequest(RunbookStatus.ACTIVE));

        assertThatThrownBy(() -> service.updateStatus(1L, "rollback", 1,
                new RunbookStatusUpdateRequest(RunbookStatus.ACTIVE)))
                .isInstanceOf(RunbookException.class);
        assertThat(service.getRevision(1L, "rollback", 2).status()).isEqualTo(RunbookStatus.ACTIVE);
    }

    @Test
    void retiredRevisionIsExcludedFromActiveList() {
        service.create(1L, createRequest("retired", RunbookStatus.ACTIVE));
        service.updateStatus(1L, "retired", 1, new RunbookStatusUpdateRequest(RunbookStatus.RETIRED));
        assertThat(service.getActiveRunbooks(1L)).isEmpty();
    }

    @Test
    void competingVersionCreationIsSerializedByDocumentLock() throws Exception {
        service.create(1L, createRequest("competition", RunbookStatus.ACTIVE));
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> service.createVersion(
                    1L, "competition", revisionRequest(RunbookStatus.ACTIVE)));
            var second = executor.submit(() -> service.createVersion(
                    1L, "competition", revisionRequest(RunbookStatus.ACTIVE)));

            assertThat(List.of(first.get().version(), second.get().version())).containsExactlyInAnyOrder(2, 3);
        }
        assertThat(service.getActiveRunbooks(1L))
                .extracting(RunbookResponse::version)
                .containsExactly(3);
    }

    private RunbookCreateRequest createRequest(String id, RunbookStatus status) {
        RunbookRevisionRequest revision = revisionRequest(status);
        return new RunbookCreateRequest(id, revision.title(), revision.documentType(), revision.serviceName(),
                revision.errorTypes(), revision.status(), revision.content());
    }

    private RunbookRevisionRequest revisionRequest(RunbookStatus status) {
        return new RunbookRevisionRequest("합성 timeout 대응", RunbookDocumentType.RUNBOOK, "order-api",
                List.of("TIMEOUT"), status, RunbookContentValidatorTest.validContent());
    }
}
