package com.monit.pingbell.incident.repository;

import com.monit.pingbell.incident.domain.IncidentLogAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface IncidentLogAnalysisRepository extends JpaRepository<IncidentLogAnalysis, Long> {
    Optional<IncidentLogAnalysis> findByIncidentId(Long incidentId);
}
