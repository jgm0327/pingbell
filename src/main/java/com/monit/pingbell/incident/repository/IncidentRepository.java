package com.monit.pingbell.incident.repository;

import com.monit.pingbell.incident.domain.Incident;
import com.monit.pingbell.incident.domain.IncidentStatus;
import com.monit.pingbell.monitor.domain.Monitor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface IncidentRepository extends JpaRepository<Incident, Long> {
    Optional<Incident> findByMonitorAndStatus(Monitor monitor, IncidentStatus status);
    boolean existsByMonitorAndStatus(Monitor monitor, IncidentStatus status);
}
