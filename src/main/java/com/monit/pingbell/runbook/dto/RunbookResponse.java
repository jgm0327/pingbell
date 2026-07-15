package com.monit.pingbell.runbook.dto;

import com.monit.pingbell.runbook.domain.*;
import java.time.Instant;
import java.util.List;

public record RunbookResponse(
        String documentId,
        String tenantId,
        String ownerId,
        String title,
        RunbookDocumentType documentType,
        String serviceName,
        List<String> errorTypes,
        int version,
        RunbookStatus status,
        String content,
        Instant updatedAt
) {
    public static RunbookResponse from(RunbookRevision revision) {
        return new RunbookResponse(
                revision.getDocument().getDocumentId(), revision.getDocument().getTenantId().toString(),
                revision.getDocument().getOwnerId().toString(), revision.getTitle(), revision.getDocumentType(),
                revision.getServiceName(), List.copyOf(revision.getErrorTypes()), revision.getVersion(),
                revision.getStatus(), revision.getContent(), revision.getUpdatedAt());
    }
}
