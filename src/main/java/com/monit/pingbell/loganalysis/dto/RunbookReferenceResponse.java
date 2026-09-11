package com.monit.pingbell.loganalysis.dto;

/**
 * A Runbook document that the AI response actually relied on, revalidated against the authenticated
 * Tenant's current ACTIVE/latest revision at response time.
 */
public record RunbookReferenceResponse(
        String documentId,
        String title,
        int version
) {
}
