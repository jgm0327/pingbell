package com.monit.pingbell.runbook.repository;

import com.monit.pingbell.runbook.domain.RunbookRevision;
import com.monit.pingbell.runbook.domain.RunbookStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface RunbookRevisionRepository extends JpaRepository<RunbookRevision, Long> {
    Optional<RunbookRevision> findFirstByDocumentIdOrderByVersionDesc(Long documentId);
    Optional<RunbookRevision> findByDocumentTenantIdAndDocumentDocumentIdAndVersion(
            Long tenantId, String documentId, Integer version);
    Optional<RunbookRevision> findByDocumentIdAndStatus(Long documentId, RunbookStatus status);
    List<RunbookRevision> findAllByDocumentTenantIdAndStatusOrderByUpdatedAtDesc(Long tenantId, RunbookStatus status);
}
