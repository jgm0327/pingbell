package com.monit.pingbell.runbook.repository;

import com.monit.pingbell.runbook.domain.RunbookDocument;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface RunbookDocumentRepository extends JpaRepository<RunbookDocument, Long> {
    boolean existsByTenantIdAndDocumentId(Long tenantId, String documentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from RunbookDocument d where d.tenantId = :tenantId and d.documentId = :documentId")
    Optional<RunbookDocument> findOwnedForUpdate(@Param("tenantId") Long tenantId,
                                                  @Param("documentId") String documentId);
}
