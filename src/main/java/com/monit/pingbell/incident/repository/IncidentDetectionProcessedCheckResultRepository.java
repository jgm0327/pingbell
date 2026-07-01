package com.monit.pingbell.incident.repository;

import com.monit.pingbell.incident.domain.IncidentDetectionProcessedCheckResult;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IncidentDetectionProcessedCheckResultRepository
        extends JpaRepository<IncidentDetectionProcessedCheckResult, Long> {
    boolean existsByCheckResultId(Long checkResultId);
}
