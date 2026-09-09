package com.monit.pingbell.incident.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.monit.pingbell.incident.domain.IncidentLogAnalysis;
import com.monit.pingbell.incident.domain.IncidentLogAnalysisStatus;
import com.monit.pingbell.incident.dto.IncidentLogAnalysisResponse;
import com.monit.pingbell.incident.repository.IncidentLogAnalysisRepository;
import com.monit.pingbell.loganalysis.dto.LogAnalysisResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IncidentLogAnalysisResultServiceTest {

    @Mock
    private IncidentLogAnalysisRepository repository;

    private IncidentLogAnalysisResultService service;

    @BeforeEach
    void setUp() {
        service = new IncidentLogAnalysisResultService(repository, new ObjectMapper());
    }

    @Test
    void markProcessingSavesAProcessingRow() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 25, 9, 0);

        service.markProcessing(100L, now);

        ArgumentCaptor<IncidentLogAnalysis> captor = ArgumentCaptor.forClass(IncidentLogAnalysis.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getIncidentId()).isEqualTo(100L);
        assertThat(captor.getValue().getStatus()).isEqualTo(IncidentLogAnalysisStatus.PROCESSING);
        assertThat(captor.getValue().getRequestedAt()).isEqualTo(now);
    }

    @Test
    void markCompletedUpdatesExistingRowWithSerializedResult() {
        IncidentLogAnalysis existing = IncidentLogAnalysis.processing(100L, LocalDateTime.of(2026, 8, 25, 9, 0));
        when(repository.findByIncidentId(100L)).thenReturn(Optional.of(existing));
        LogAnalysisResponse response = new LogAnalysisResponse(
                12L, "summary", List.of(), List.of(), List.of(), List.of("warning"), List.of(), false, 10, 10);
        LocalDateTime completedAt = LocalDateTime.of(2026, 8, 25, 9, 1);

        service.markCompleted(100L, response, completedAt);

        assertThat(existing.getStatus()).isEqualTo(IncidentLogAnalysisStatus.COMPLETED);
        assertThat(existing.getCompletedAt()).isEqualTo(completedAt);
        assertThat(existing.getResultJson()).contains("\"summary\":\"summary\"");
    }

    @Test
    void markCompletedDoesNothingWhenRowIsMissing() {
        when(repository.findByIncidentId(100L)).thenReturn(Optional.empty());
        LogAnalysisResponse response = new LogAnalysisResponse(
                12L, "summary", List.of(), List.of(), List.of(), List.of("warning"), List.of(), false, 10, 10);

        service.markCompleted(100L, response, LocalDateTime.now());

        // no exception - the missing row is only logged, since this must never break the caller
    }

    @Test
    void markFailedTruncatesLongErrorMessages() {
        IncidentLogAnalysis existing = IncidentLogAnalysis.processing(100L, LocalDateTime.of(2026, 8, 25, 9, 0));
        when(repository.findByIncidentId(100L)).thenReturn(Optional.of(existing));
        String longMessage = "x".repeat(2000);
        LocalDateTime completedAt = LocalDateTime.of(2026, 8, 25, 9, 1);

        service.markFailed(100L, longMessage, completedAt);

        assertThat(existing.getStatus()).isEqualTo(IncidentLogAnalysisStatus.FAILED);
        assertThat(existing.getErrorMessage()).hasSize(1000);
    }

    @Test
    void markFailedFallsBackToDefaultMessageWhenNull() {
        IncidentLogAnalysis existing = IncidentLogAnalysis.processing(100L, LocalDateTime.of(2026, 8, 25, 9, 0));
        when(repository.findByIncidentId(100L)).thenReturn(Optional.of(existing));

        service.markFailed(100L, null, LocalDateTime.now());

        assertThat(existing.getErrorMessage()).isEqualTo("Unknown error.");
    }

    @Test
    void findMapsPersistedRowBackToResponse() {
        IncidentLogAnalysis existing = IncidentLogAnalysis.processing(100L, LocalDateTime.of(2026, 8, 25, 9, 0));
        LogAnalysisResponse response = new LogAnalysisResponse(
                12L, "summary", List.of(), List.of(), List.of(), List.of("warning"), List.of(), false, 10, 10);
        service = new IncidentLogAnalysisResultService(repository, new ObjectMapper());
        when(repository.findByIncidentId(100L)).thenReturn(Optional.of(existing));
        service.markCompleted(100L, response, LocalDateTime.of(2026, 8, 25, 9, 1));

        Optional<IncidentLogAnalysisResponse> found = service.find(100L);

        assertThat(found).isPresent();
        assertThat(found.get().status()).isEqualTo(IncidentLogAnalysisStatus.COMPLETED);
        assertThat(found.get().result().summary()).isEqualTo("summary");
    }

    @Test
    void findReturnsEmptyWhenNoRowExists() {
        when(repository.findByIncidentId(100L)).thenReturn(Optional.empty());

        assertThat(service.find(100L)).isEmpty();
    }
}
