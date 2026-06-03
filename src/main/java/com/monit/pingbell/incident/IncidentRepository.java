package com.monit.pingbell.incident;

import com.monit.pingbell.monitor.Monitor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface IncidentRepository extends JpaRepository<Incident, Long> {
    Optional<Incident> findByMonitorAndStatus(Monitor monitor, IncidentStatus status);
    boolean existsByMonitorAndStatus(Monitor monitor, IncidentStatus status);
}
