package com.monit.pingbell.incident.service;

import com.monit.pingbell.loganalysis.service.LogAnalysisService;
import com.monit.pingbell.logingestion.service.LogIngestionBufferService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Automatically analyzes a Monitor's recently ingested log buffer (see the log ingestion
 * feature, Issue 1/2) the moment an Incident opens, so nobody has to notice the incident and
 * manually upload a log file for the AI analysis to be useful. Runs off the calling thread
 * (Incident detection's own transaction) so a slow LLM call never delays incident creation or
 * notification dispatch, and every failure is swallowed here - this must never affect Incident
 * detection, which has already fully completed by the time this runs.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IncidentLogAnalysisTriggerService {

    private final LogIngestionBufferService bufferService;
    private final LogAnalysisService logAnalysisService;
    private final IncidentLogAnalysisResultService resultService;
    private final Clock clock;

    @Async("logAnalysisExecutor")
    public void analyzeAfterIncidentOpened(Long incidentId, Long tenantId, Long monitorId, LocalDateTime now) {
        List<String> bufferedLines = bufferService.readAll(tenantId, monitorId);
        if (bufferedLines.isEmpty()) {
            log.debug("No buffered logs for automatic incident analysis; skipping. incidentId={}, monitorId={}",
                    incidentId, monitorId);
            return;
        }

        resultService.markProcessing(incidentId, now);
        try {
            String content = String.join("\n", bufferedLines);
            var response = logAnalysisService.analyzeBufferedContent(tenantId, monitorId, content);
            resultService.markCompleted(incidentId, response, LocalDateTime.now(clock));
        } catch (Exception e) {
            log.warn("Automatic incident log analysis failed. incidentId={}, monitorId={}", incidentId, monitorId, e);
            resultService.markFailed(incidentId, e.getMessage(), LocalDateTime.now(clock));
        }
    }
}
