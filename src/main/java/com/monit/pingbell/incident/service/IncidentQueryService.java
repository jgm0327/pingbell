package com.monit.pingbell.incident.service;

import com.monit.pingbell.incident.dto.IncidentResponse;
import com.monit.pingbell.incident.domain.IncidentStatus;
import com.monit.pingbell.incident.repository.IncidentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class IncidentQueryService {
    private final IncidentRepository incidentRepository;

    @Transactional(readOnly = true)
    public List<IncidentResponse> getIncidents(Long memberId, IncidentStatus status) {
        var incidents = status == null
                ? incidentRepository.findAllByMonitorMemberIdOrderByStartedAtDesc(memberId)
                : incidentRepository.findAllByMonitorMemberIdAndStatusOrderByStartedAtDesc(memberId, status);

        return incidents
                .stream()
                .map(IncidentResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public IncidentResponse getIncident(Long incidentId) {
        return incidentRepository.findById(incidentId)
                .map(IncidentResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Incident not found."));
    }

    @Transactional(readOnly = true)
    public List<IncidentResponse> getMonitorIncidents(Long monitorId, IncidentStatus status) {
        var incidents = status == null
                ? incidentRepository.findAllByMonitorIdOrderByStartedAtDesc(monitorId)
                : incidentRepository.findAllByMonitorIdAndStatusOrderByStartedAtDesc(monitorId, status);

        return incidents
                .stream()
                .map(IncidentResponse::from)
                .toList();
    }
}
