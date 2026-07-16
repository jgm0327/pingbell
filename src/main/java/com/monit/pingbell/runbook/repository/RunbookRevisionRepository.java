package com.monit.pingbell.runbook.repository;

import com.monit.pingbell.runbook.domain.RunbookRevision;
import com.monit.pingbell.runbook.domain.RunbookStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface RunbookRevisionRepository extends JpaRepository<RunbookRevision, Long> {
    Optional<RunbookRevision> findFirstByDocumentIdOrderByVersionDesc(Long documentId);
    Optional<RunbookRevision> findByDocumentTenantIdAndDocumentDocumentIdAndVersion(
            Long tenantId, String documentId, Integer version);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RunbookRevision r join fetch r.document d " +
            "where d.tenantId = :tenantId and d.documentId = :documentId " +
            "and r.version = :version and r.status = com.monit.pingbell.runbook.domain.RunbookStatus.ACTIVE")
    Optional<RunbookRevision> findActiveOwnedForUpdate(@Param("tenantId") Long tenantId,
                                                       @Param("documentId") String documentId,
                                                       @Param("version") Integer version);
    Optional<RunbookRevision> findByDocumentIdAndStatus(Long documentId, RunbookStatus status);
    List<RunbookRevision> findAllByDocumentTenantIdAndStatusOrderByUpdatedAtDesc(Long tenantId, RunbookStatus status);
}
