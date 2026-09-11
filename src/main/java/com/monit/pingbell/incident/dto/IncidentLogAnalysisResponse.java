package com.monit.pingbell.incident.dto;

import com.monit.pingbell.incident.domain.IncidentLogAnalysisStatus;
import com.monit.pingbell.loganalysis.dto.LogAnalysisResponse;

import java.time.LocalDateTime;

/** {@code result} is populated only when {@code status == COMPLETED}; {@code errorMessage} only
 * when {@code status == FAILED}. This is participant-visible - see IncidentLogAnalysisTriggerService
 * for how it's produced. */
public record IncidentLogAnalysisResponse(
        Long incidentId,
        IncidentLogAnalysisStatus status,
        LogAnalysisResponse result,
        String errorMessage,
        LocalDateTime requestedAt,
        LocalDateTime completedAt
) {
}
