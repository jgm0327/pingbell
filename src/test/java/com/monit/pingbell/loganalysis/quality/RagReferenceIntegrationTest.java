package com.monit.pingbell.loganalysis.quality;

import com.monit.pingbell.loganalysis.client.LogAnalysisClient;
import com.monit.pingbell.loganalysis.client.LogAnalysisClientResult;
import com.monit.pingbell.loganalysis.dto.Confidence;
import com.monit.pingbell.loganalysis.dto.LogAnalysisResponse;
import com.monit.pingbell.loganalysis.dto.RecommendedActionResponse;
import com.monit.pingbell.loganalysis.dto.RunbookReferenceResponse;
import com.monit.pingbell.loganalysis.dto.SuspectedCauseResponse;
import com.monit.pingbell.loganalysis.runbook.RunbookContextChunk;
import com.monit.pingbell.loganalysis.runbook.RunbookContextService;
import com.monit.pingbell.loganalysis.runbook.RunbookReferenceValidator;
import com.monit.pingbell.loganalysis.service.LogAnalysisService;
import com.monit.pingbell.loganalysis.service.LogFileValidator;
import com.monit.pingbell.loganalysis.service.LogPreprocessor;
import com.monit.pingbell.monitor.domain.Monitor;
import com.monit.pingbell.monitor.repository.MonitorRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Mock-based regression test for Issue 6 (docs/ai/rag-implementation-issues.md) - verifies that
 * when a (faked) model response cites a Runbook chunk id that was never part of the context
 * actually provided to it, LogAnalysisService's real wiring surfaces only what
 * RunbookReferenceValidator decides to keep, never the raw unfiltered model citation list. Never
 * calls a real model - the 4 synthetic quality fixtures and a scripted LogAnalysisClient stand in
 * for it, so this always runs in default CI (no API key needed).
 */
@ExtendWith(MockitoExtension.class)
class RagReferenceIntegrationTest {

    @Mock
    private MonitorRepository monitorRepository;
    @Mock
    private LogFileValidator fileValidator;
    @Mock
    private LogAnalysisClient analysisClient;
    @Mock
    private RunbookContextService runbookContextService;
    @Mock
    private RunbookReferenceValidator runbookReferenceValidator;

    private final LogPreprocessor preprocessor = new LogPreprocessor();

    @Test
    void onlySurfacesReferencesTheValidatorActuallyApproves() throws IOException {
        Long tenantId = 1L;
        Long monitorId = 10L;
        when(monitorRepository.findByIdAndMemberIdAndDeletedAtIsNull(monitorId, tenantId))
                .thenReturn(Optional.of(mock(Monitor.class)));

        RunbookContextChunk providedChunk = new RunbookContextChunk(
                "chunk-timeout-1", "doc-timeout", "타임아웃 대응", 1, "대응 절차", "네트워크와 upstream 상태를 먼저 확인한다.");
        List<RunbookContextChunk> providedContext = List.of(providedChunk);
        when(runbookContextService.buildContext(eq(tenantId), any())).thenReturn(providedContext);

        // The (faked) model cites both the one chunk it was actually shown and one it was never
        // shown - exactly the failure mode RunbookReferenceValidator exists to catch. This test
        // doesn't re-verify the validator's own filtering rules (RunbookReferenceValidatorTest
        // already does that in depth) - it verifies LogAnalysisService passes the model's full,
        // unfiltered citation list through to the validator and returns exactly what comes back.
        LogAnalysisClientResult modelResult = new LogAnalysisClientResult(
                "요약",
                List.of(new SuspectedCauseResponse("네트워크 지연", Confidence.MEDIUM, "타임아웃 반복")),
                List.of(new RecommendedActionResponse(1, "네트워크 상태 확인", null)),
                List.of("HttpConnectTimeoutException"),
                List.of("추가 로그가 필요합니다."),
                List.of("chunk-timeout-1", "chunk-never-shown-to-model")
        );
        when(analysisClient.analyze(any(), any(), eq(providedContext))).thenReturn(modelResult);
        when(runbookReferenceValidator.validate(eq(tenantId), eq(providedContext), eq(modelResult.referencedRunbookChunkIds())))
                .thenReturn(List.of(new RunbookReferenceResponse("doc-timeout", "타임아웃 대응", 1)));

        LogAnalysisService service = new LogAnalysisService(
                monitorRepository, fileValidator, preprocessor, analysisClient,
                runbookContextService, runbookReferenceValidator);

        LogAnalysisResponse response = service.analyzeBufferedContent(
                tenantId, monitorId, QualityFixtures.resource("timeout.log"));

        assertThat(response.references()).extracting(RunbookReferenceResponse::documentId).containsExactly("doc-timeout");
    }

    @Test
    void degradesToEmptyContextWithoutFailingWhenRunbookSearchFindsNothing() throws IOException {
        Long tenantId = 2L;
        Long monitorId = 20L;
        when(monitorRepository.findByIdAndMemberIdAndDeletedAtIsNull(monitorId, tenantId))
                .thenReturn(Optional.of(mock(Monitor.class)));
        when(runbookContextService.buildContext(eq(tenantId), any())).thenReturn(List.of());

        LogAnalysisClientResult modelResult = new LogAnalysisClientResult(
                "요약", List.of(), List.of(), List.of("evidence"), List.of("한계 명시"), List.of());
        when(analysisClient.analyze(any(), any(), eq(List.of()))).thenReturn(modelResult);
        when(runbookReferenceValidator.validate(eq(tenantId), eq(List.of()), eq(List.of()))).thenReturn(List.of());

        LogAnalysisService service = new LogAnalysisService(
                monitorRepository, fileValidator, preprocessor, analysisClient,
                runbookContextService, runbookReferenceValidator);

        LogAnalysisResponse response = service.analyzeBufferedContent(
                tenantId, monitorId, QualityFixtures.resource("out-of-memory.log"));

        assertThat(response.references()).isEmpty();
        assertThat(response.summary()).isEqualTo("요약");
    }
}
