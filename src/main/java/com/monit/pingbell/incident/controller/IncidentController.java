package com.monit.pingbell.incident.controller;

import com.monit.pingbell.global.security.auth.AuthenticatedMember;
import com.monit.pingbell.incident.dto.IncidentLogAnalysisResponse;
import com.monit.pingbell.incident.dto.IncidentResponse;
import com.monit.pingbell.incident.domain.IncidentStatus;
import com.monit.pingbell.incident.service.IncidentLogAnalysisResultService;
import com.monit.pingbell.incident.service.IncidentQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class IncidentController {
    private final IncidentQueryService incidentQueryService;
    private final IncidentLogAnalysisResultService incidentLogAnalysisResultService;

    @GetMapping("/api/incidents")
    public List<IncidentResponse> getIncidents(
            @AuthenticationPrincipal AuthenticatedMember member,
            @RequestParam(required = false) IncidentStatus status
    ) {
        return incidentQueryService.getIncidents(member.id(), status);
    }

    @GetMapping("/api/incidents/{incidentId}")
    public IncidentResponse getIncident(@PathVariable Long incidentId) {
        return incidentQueryService.getIncident(incidentId);
    }

    @GetMapping("/api/incidents/{incidentId}/log-analysis")
    public IncidentLogAnalysisResponse getIncidentLogAnalysis(@PathVariable Long incidentId) {
        return incidentLogAnalysisResultService.find(incidentId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "No automatic log analysis for this incident."));
    }

    @GetMapping("/api/monitors/{monitorId}/incidents")
    public List<IncidentResponse> getMonitorIncidents(
            @PathVariable Long monitorId,
            @RequestParam(required = false) IncidentStatus status
    ) {
        return incidentQueryService.getMonitorIncidents(monitorId, status);
    }
}
