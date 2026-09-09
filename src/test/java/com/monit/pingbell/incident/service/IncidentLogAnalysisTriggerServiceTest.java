package com.monit.pingbell.incident.service;

import com.monit.pingbell.loganalysis.dto.LogAnalysisResponse;
import com.monit.pingbell.loganalysis.service.LogAnalysisService;
import com.monit.pingbell.logingestion.service.LogIngestionBufferService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IncidentLogAnalysisTriggerServiceTest {

    @Mock
    private LogIngestionBufferService bufferService;

    @Mock
    private LogAnalysisService logAnalysisService;

    @Mock
    private IncidentLogAnalysisResultService resultService;

    private final Clock clock = Clock.fixed(Instant.parse("2026-08-25T00:00:00Z"), ZoneOffset.UTC);
    private final LocalDateTime openedAt = LocalDateTime.of(2026, 8, 25, 9, 0);

    private IncidentLogAnalysisTriggerService triggerService;

    @BeforeEach
    void setUp() {
        triggerService = new IncidentLogAnalysisTriggerService(bufferService, logAnalysisService, resultService, clock);
    }

    @Test
    void skipsEntirelyWhenBufferIsEmpty() {
        when(bufferService.readAll(1L, 12L)).thenReturn(List.of());

        triggerService.analyzeAfterIncidentOpened(100L, 1L, 12L, openedAt);

        verifyNoInteractions(logAnalysisService, resultService);
    }

    @Test
    void marksProcessingThenCompletedOnSuccess() {
        when(bufferService.readAll(1L, 12L)).thenReturn(List.of("line one", "line two"));
        LogAnalysisResponse response = new LogAnalysisResponse(
                12L, "summary", List.of(), List.of(), List.of(), List.of("warning"), List.of(), false, 10, 10);
        when(logAnalysisService.analyzeBufferedContent(1L, 12L, "line one\nline two")).thenReturn(response);

        triggerService.analyzeAfterIncidentOpened(100L, 1L, 12L, openedAt);

        verify(resultService).markProcessing(100L, openedAt);
        verify(resultService).markCompleted(100L, response, LocalDateTime.now(clock));
        verify(resultService, never()).markFailed(any(), any(), any());
    }

    @Test
    void marksFailedWhenAnalysisThrowsAndNeverPropagates() {
        when(bufferService.readAll(1L, 12L)).thenReturn(List.of("line one"));
        when(logAnalysisService.analyzeBufferedContent(1L, 12L, "line one"))
                .thenThrow(new RuntimeException("boom"));

        triggerService.analyzeAfterIncidentOpened(100L, 1L, 12L, openedAt);

        verify(resultService).markProcessing(100L, openedAt);
        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(resultService).markFailed(eq(100L), messageCaptor.capture(), eq(LocalDateTime.now(clock)));
        assertThat(messageCaptor.getValue()).isEqualTo("boom");
    }
}
