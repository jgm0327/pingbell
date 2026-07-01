package com.monit.pingbell.incident.repository;

import com.monit.pingbell.incident.domain.Incident;
import com.monit.pingbell.incident.domain.IncidentStatus;
import com.monit.pingbell.monitor.domain.Monitor;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface IncidentRepository extends JpaRepository<Incident, Long> {
    @Override
    @EntityGraph(attributePaths = {"monitor", "monitor.member"})
    Optional<Incident> findById(Long incidentId);

    Optional<Incident> findByMonitorAndStatus(Monitor monitor, IncidentStatus status);
    boolean existsByMonitorAndStatus(Monitor monitor, IncidentStatus status);

    List<Incident> findAllByMonitorMemberIdOrderByStartedAtDesc(Long memberId);

    List<Incident> findAllByMonitorMemberIdAndStatusOrderByStartedAtDesc(Long memberId, IncidentStatus status);

    List<Incident> findAllByMonitorIdAndMonitorMemberIdOrderByStartedAtDesc(Long monitorId, Long memberId);

    List<Incident> findAllByMonitorIdAndStatusOrderByStartedAtDesc(Long monitorId, IncidentStatus status);

    List<Incident> findAllByMonitorIdOrderByStartedAtDesc(Long monitorId);

    Optional<Incident> findByIdAndMonitorMemberId(Long incidentId, Long memberId);

    boolean existsByIdAndMonitorMemberId(Long incidentId, Long memberId);

    long countByMonitorMemberIdAndStatus(Long memberId, IncidentStatus status);

    long countByMonitorMemberIdAndStartedAtGreaterThanEqual(Long memberId, LocalDateTime since);

    long countByMonitorMemberIdAndResolvedAtGreaterThanEqual(Long memberId, LocalDateTime since);
}
