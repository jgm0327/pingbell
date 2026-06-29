package com.monit.pingbell.incident.service;

import com.monit.pingbell.incident.domain.IncidentStatus;
import com.monit.pingbell.incident.repository.IncidentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IncidentQueryServiceTest {

    @Mock
    private IncidentRepository incidentRepository;

    @InjectMocks
    private IncidentQueryService incidentQueryService;

    @Test
    void getIncidentsCanFilterByStatus() {
        Long memberId = 1L;

        when(incidentRepository.findAllByMonitorMemberIdAndStatusOrderByStartedAtDesc(memberId, IncidentStatus.OPEN))
                .thenReturn(List.of());

        var responses = incidentQueryService.getIncidents(memberId, IncidentStatus.OPEN);

        assertThat(responses).isEmpty();
    }

    @Test
    void getMonitorIncidentsCanFilterByStatus() {
        Long monitorId = 10L;

        when(incidentRepository.findAllByMonitorIdAndStatusOrderByStartedAtDesc(monitorId, IncidentStatus.RESOLVED))
                .thenReturn(List.of());

        var responses = incidentQueryService.getMonitorIncidents(monitorId, IncidentStatus.RESOLVED);

        assertThat(responses).isEmpty();
    }
}
