package com.monit.pingbell.loganalysis.service;

import com.monit.pingbell.loganalysis.client.LogAnalysisClient;
import com.monit.pingbell.loganalysis.client.LogAnalysisClientResult;
import com.monit.pingbell.loganalysis.dto.LogAnalysisRequest;
import com.monit.pingbell.loganalysis.dto.LogAnalysisResponse;
import com.monit.pingbell.loganalysis.exception.LogAnalysisException;
import com.monit.pingbell.monitor.repository.MonitorRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class LogAnalysisService {

    private final MonitorRepository monitorRepository;
    private final LogFileValidator fileValidator;
    private final LogPreprocessor preprocessor;
    private final LogAnalysisClient analysisClient;

    public LogAnalysisService(
            MonitorRepository monitorRepository,
            LogFileValidator fileValidator,
            LogPreprocessor preprocessor,
            LogAnalysisClient analysisClient
    ) {
        this.monitorRepository = monitorRepository;
        this.fileValidator = fileValidator;
        this.preprocessor = preprocessor;
        this.analysisClient = analysisClient;
    }

    public LogAnalysisResponse analyze(Long memberId, Long monitorId, LogAnalysisRequest request) {
        validateOwnedMonitor(memberId, monitorId);
        ValidatedLogFile validated = fileValidator.validate(request == null ? null : request.getFile());
        ProcessedLog processed = preprocessor.process(validated.content());
        String question = request == null ? null : preprocessor.mask(request.getQuestion());

        LogAnalysisClientResult result = analysisClient.analyze(processed.content(), question);
        return new LogAnalysisResponse(
                monitorId,
                result.summary(),
                result.suspectedCauses(),
                result.recommendedActions(),
                result.evidence(),
                result.warnings(),
                processed.truncated(),
                validated.originalSizeBytes(),
                processed.analyzedCharacters()
        );
    }

    private void validateOwnedMonitor(Long memberId, Long monitorId) {
        if (monitorRepository.findByIdAndMemberIdAndDeletedAtIsNull(monitorId, memberId).isEmpty()) {
            throw new LogAnalysisException(HttpStatus.NOT_FOUND, "MONITOR_NOT_FOUND", "Monitor not found.");
        }
    }
}
