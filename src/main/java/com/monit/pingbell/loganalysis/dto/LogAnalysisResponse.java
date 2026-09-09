package com.monit.pingbell.loganalysis.dto;

import java.util.List;

public record LogAnalysisResponse(
        Long monitorId,
        String summary,
        List<SuspectedCauseResponse> suspectedCauses,
        List<RecommendedActionResponse> recommendedActions,
        List<String> evidence,
        List<String> warnings,
        List<RunbookReferenceResponse> references,
        boolean truncated,
        long originalSizeBytes,
        int analyzedCharacters
) {
}
