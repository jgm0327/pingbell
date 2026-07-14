package com.monit.pingbell.loganalysis.service;

import com.monit.pingbell.loganalysis.client.LogAnalysisClient;
import com.monit.pingbell.loganalysis.client.LogAnalysisClientResult;
import com.monit.pingbell.loganalysis.dto.Confidence;
import com.monit.pingbell.loganalysis.dto.LogAnalysisRequest;
import com.monit.pingbell.loganalysis.dto.RecommendedActionResponse;
import com.monit.pingbell.loganalysis.dto.SuspectedCauseResponse;
import com.monit.pingbell.loganalysis.exception.LogAnalysisException;
import com.monit.pingbell.monitor.domain.Monitor;
import com.monit.pingbell.monitor.repository.MonitorRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LogAnalysisServiceTest {

    @Mock
    private MonitorRepository monitorRepository;

    @Mock
    private LogAnalysisClient analysisClient;

    @Test
    void analyzesOwnedMonitorWithOnlyMaskedInput() {
        LogAnalysisService service = service();
        LogAnalysisRequest request = request("""
                password=hunter2
                user@example.com from 10.0.0.1
                java.sql.SQLTimeoutException: connection unavailable
                """, "Check admin@example.com and bearer question-token");
        when(monitorRepository.findByIdAndMemberIdAndDeletedAtIsNull(12L, 1L))
                .thenReturn(Optional.of(org.mockito.Mockito.mock(Monitor.class)));
        when(analysisClient.analyze(anyString(), anyString())).thenReturn(result());

        var response = service.analyze(1L, 12L, request);

        ArgumentCaptor<String> log = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> question = ArgumentCaptor.forClass(String.class);
        verify(analysisClient).analyze(log.capture(), question.capture());
        assertThat(log.getValue()).doesNotContain("hunter2", "user@example.com", "10.0.0.1");
        assertThat(question.getValue()).doesNotContain("admin@example.com", "question-token");
        assertThat(response.monitorId()).isEqualTo(12L);
        assertThat(response.summary()).isEqualTo("Database connectivity may be degraded.");
        assertThat(response.originalSizeBytes()).isEqualTo(request.getFile().getFirst().getSize());
        assertThat(response.truncated()).isFalse();
    }

    @Test
    void rejectsForeignOrDeletedMonitorBeforeReadingFile() {
        LogAnalysisService service = service();
        when(monitorRepository.findByIdAndMemberIdAndDeletedAtIsNull(12L, 2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.analyze(2L, 12L, request("error", null)))
                .isInstanceOfSatisfying(LogAnalysisException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("MONITOR_NOT_FOUND"));
        verify(analysisClient, never()).analyze(anyString(), anyString());
    }

    private LogAnalysisService service() {
        return new LogAnalysisService(
                monitorRepository,
                new LogFileValidator(),
                new LogPreprocessor(),
                analysisClient
        );
    }

    private LogAnalysisRequest request(String content, String question) {
        LogAnalysisRequest request = new LogAnalysisRequest();
        request.setFile(List.of(new MockMultipartFile(
                "file", "server.log", "text/plain", content.getBytes(StandardCharsets.UTF_8)
        )));
        request.setQuestion(question);
        return request;
    }

    private LogAnalysisClientResult result() {
        return new LogAnalysisClientResult(
                "Database connectivity may be degraded.",
                List.of(new SuspectedCauseResponse("Pool exhaustion", Confidence.HIGH, "Timeout repeated.")),
                List.of(new RecommendedActionResponse(1, "Inspect pool metrics.", null)),
                List.of("connection unavailable"),
                List.of("The log alone is not conclusive.")
        );
    }
}
