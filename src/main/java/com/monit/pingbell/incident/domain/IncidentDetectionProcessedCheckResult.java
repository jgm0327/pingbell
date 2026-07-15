package com.monit.pingbell.incident.domain;

import com.monit.pingbell.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "incident_detection_processed_check_results",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_incident_detection_processed_check_result",
                columnNames = "check_result_id"
        )
)
public class IncidentDetectionProcessedCheckResult extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "check_result_id", nullable = false)
    private Long checkResultId;

    public IncidentDetectionProcessedCheckResult(Long checkResultId) {
        this.checkResultId = checkResultId;
    }
}
