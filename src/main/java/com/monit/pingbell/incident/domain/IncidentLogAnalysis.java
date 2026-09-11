package com.monit.pingbell.incident.domain;

import com.monit.pingbell.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * The result of automatically analyzing an Incident's recently ingested logs when it opens (see
 * IncidentLogAnalysisTriggerService). Entirely separate from the user-triggered, on-demand log
 * analysis flow (LogAnalysisService) - a failure here never touches Incident detection itself.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "incident_log_analyses")
public class IncidentLogAnalysis extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "incident_id", nullable = false, updatable = false)
    private Long incidentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private IncidentLogAnalysisStatus status;

    @Column(name = "result_json", columnDefinition = "text")
    private String resultJson;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private LocalDateTime requestedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    private IncidentLogAnalysis(Long incidentId, LocalDateTime requestedAt) {
        this.incidentId = incidentId;
        this.status = IncidentLogAnalysisStatus.PROCESSING;
        this.requestedAt = requestedAt;
    }

    public static IncidentLogAnalysis processing(Long incidentId, LocalDateTime requestedAt) {
        return new IncidentLogAnalysis(incidentId, requestedAt);
    }

    public void complete(String resultJson, LocalDateTime completedAt) {
        this.status = IncidentLogAnalysisStatus.COMPLETED;
        this.resultJson = resultJson;
        this.completedAt = completedAt;
    }

    public void fail(String errorMessage, LocalDateTime completedAt) {
        this.status = IncidentLogAnalysisStatus.FAILED;
        this.errorMessage = errorMessage;
        this.completedAt = completedAt;
    }
}
