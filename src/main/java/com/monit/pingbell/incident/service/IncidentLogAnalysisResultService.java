package com.monit.pingbell.incident.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.monit.pingbell.incident.domain.IncidentLogAnalysis;
import com.monit.pingbell.incident.dto.IncidentLogAnalysisResponse;
import com.monit.pingbell.incident.repository.IncidentLogAnalysisRepository;
import com.monit.pingbell.loganalysis.dto.LogAnalysisResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

/** A separate Spring bean (not just private methods on the async trigger service) so each step
 * runs in its own, correctly-proxied transaction - the trigger itself runs on a background
 * thread with no inherited transaction context. */
@Service
@RequiredArgsConstructor
@Slf4j
public class IncidentLogAnalysisResultService {

    private static final int MAX_ERROR_MESSAGE_LENGTH = 1000;

    private final IncidentLogAnalysisRepository repository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void markProcessing(Long incidentId, LocalDateTime now) {
        repository.save(IncidentLogAnalysis.processing(incidentId, now));
    }

    @Transactional
    public void markCompleted(Long incidentId, LogAnalysisResponse response, LocalDateTime now) {
        findOrLog(incidentId).ifPresent(analysis -> analysis.complete(writeJson(response), now));
    }

    @Transactional
    public void markFailed(Long incidentId, String errorMessage, LocalDateTime now) {
        String truncated = errorMessage == null ? "Unknown error." : errorMessage;
        if (truncated.length() > MAX_ERROR_MESSAGE_LENGTH) {
            truncated = truncated.substring(0, MAX_ERROR_MESSAGE_LENGTH);
        }
        String finalMessage = truncated;
        findOrLog(incidentId).ifPresent(analysis -> analysis.fail(finalMessage, now));
    }

    @Transactional(readOnly = true)
    public Optional<IncidentLogAnalysisResponse> find(Long incidentId) {
        return repository.findByIncidentId(incidentId).map(this::toResponse);
    }

    private Optional<IncidentLogAnalysis> findOrLog(Long incidentId) {
        Optional<IncidentLogAnalysis> analysis = repository.findByIncidentId(incidentId);
        if (analysis.isEmpty()) {
            log.warn("No PROCESSING IncidentLogAnalysis row found to update. incidentId={}", incidentId);
        }
        return analysis;
    }

    private IncidentLogAnalysisResponse toResponse(IncidentLogAnalysis analysis) {
        LogAnalysisResponse result = analysis.getResultJson() == null ? null : readJson(analysis.getResultJson());
        return new IncidentLogAnalysisResponse(
                analysis.getIncidentId(),
                analysis.getStatus(),
                result,
                analysis.getErrorMessage(),
                analysis.getRequestedAt(),
                analysis.getCompletedAt()
        );
    }

    private String writeJson(LogAnalysisResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize LogAnalysisResponse.", e);
        }
    }

    private LogAnalysisResponse readJson(String json) {
        try {
            return objectMapper.readValue(json, LogAnalysisResponse.class);
        } catch (JsonProcessingException e) {
            log.warn("Failed to deserialize stored IncidentLogAnalysis result JSON.", e);
            return null;
        }
    }
}
