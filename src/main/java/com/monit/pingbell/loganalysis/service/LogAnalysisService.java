package com.monit.pingbell.loganalysis.service;

import com.monit.pingbell.loganalysis.client.LogAnalysisClient;
import com.monit.pingbell.loganalysis.client.LogAnalysisClientResult;
import com.monit.pingbell.loganalysis.dto.LogAnalysisRequest;
import com.monit.pingbell.loganalysis.dto.LogAnalysisResponse;
import com.monit.pingbell.loganalysis.dto.RunbookReferenceResponse;
import com.monit.pingbell.loganalysis.exception.LogAnalysisException;
import com.monit.pingbell.loganalysis.runbook.RunbookContextChunk;
import com.monit.pingbell.loganalysis.runbook.RunbookContextService;
import com.monit.pingbell.loganalysis.runbook.RunbookReferenceValidator;
import com.monit.pingbell.monitor.repository.MonitorRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Service
public class LogAnalysisService {

    private final MonitorRepository monitorRepository;
    private final LogFileValidator fileValidator;
    private final LogPreprocessor preprocessor;
    private final LogAnalysisClient analysisClient;
    private final RunbookContextService runbookContextService;
    private final RunbookReferenceValidator runbookReferenceValidator;

    public LogAnalysisService(
            MonitorRepository monitorRepository,
            LogFileValidator fileValidator,
            LogPreprocessor preprocessor,
            LogAnalysisClient analysisClient,
            RunbookContextService runbookContextService,
            RunbookReferenceValidator runbookReferenceValidator
    ) {
        this.monitorRepository = monitorRepository;
        this.fileValidator = fileValidator;
        this.preprocessor = preprocessor;
        this.analysisClient = analysisClient;
        this.runbookContextService = runbookContextService;
        this.runbookReferenceValidator = runbookReferenceValidator;
    }

    public LogAnalysisResponse analyze(Long memberId, Long monitorId, LogAnalysisRequest request) {
        validateOwnedMonitor(memberId, monitorId);
        ValidatedLogFile validated = fileValidator.validate(request == null ? null : request.getFile());
        String question = request == null ? null : request.getQuestion();
        return analyzeContent(memberId, monitorId, validated.content(), question, validated.originalSizeBytes());
    }

    /** Entry point for automatically analyzing a Monitor's recently ingested (already-masked) log
     * buffer - see {@code IncidentLogAnalysisTriggerService}. There is no uploaded file and no
     * user question, but the Tenant boundary is still enforced exactly like the upload flow. */
    public LogAnalysisResponse analyzeBufferedContent(Long tenantId, Long monitorId, String rawContent) {
        validateOwnedMonitor(tenantId, monitorId);
        long originalSizeBytes = rawContent == null ? 0 : rawContent.getBytes(StandardCharsets.UTF_8).length;
        return analyzeContent(tenantId, monitorId, rawContent, null, originalSizeBytes);
    }

    private LogAnalysisResponse analyzeContent(
            Long memberId, Long monitorId, String rawContent, String rawQuestion, long originalSizeBytes
    ) {
        ProcessedLog processed = preprocessor.process(rawContent);
        String question = rawQuestion == null ? null : preprocessor.mask(rawQuestion);

        // Tenant-scoped Runbook search; any empty result, low relevance, or search failure/timeout
        // safely degrades to an empty context so log analysis continues without RAG.
        List<RunbookContextChunk> runbookContext = runbookContextService.buildContext(
                memberId, buildRunbookQuery(question, processed.content()));

        LogAnalysisClientResult result = analysisClient.analyze(processed.content(), question, runbookContext);

        // Only chunk ids the model actually cited AND that were part of the provided context AND
        // still resolve to the Tenant's current ACTIVE/latest revision are surfaced as references.
        List<RunbookReferenceResponse> references = runbookReferenceValidator.validate(
                memberId, runbookContext, result.referencedRunbookChunkIds());

        return new LogAnalysisResponse(
                monitorId,
                result.summary(),
                result.suspectedCauses(),
                result.recommendedActions(),
                result.evidence(),
                result.warnings(),
                references,
                processed.truncated(),
                originalSizeBytes,
                processed.analyzedCharacters()
        );
    }

    private String buildRunbookQuery(String question, String logContent) {
        StringBuilder query = new StringBuilder();
        if (question != null && !question.isBlank()) {
            query.append(question).append('\n');
        }
        if (logContent != null) {
            query.append(logContent);
        }
        return query.toString();
    }

    private void validateOwnedMonitor(Long memberId, Long monitorId) {
        if (monitorRepository.findByIdAndMemberIdAndDeletedAtIsNull(monitorId, memberId).isEmpty()) {
            throw new LogAnalysisException(HttpStatus.NOT_FOUND, "MONITOR_NOT_FOUND", "Monitor not found.");
        }
    }
}
