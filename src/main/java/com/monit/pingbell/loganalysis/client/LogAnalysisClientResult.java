package com.monit.pingbell.loganalysis.client;

import com.monit.pingbell.loganalysis.dto.RecommendedActionResponse;
import com.monit.pingbell.loganalysis.dto.SuspectedCauseResponse;

import java.util.List;

public record LogAnalysisClientResult(
        String summary,
        List<SuspectedCauseResponse> suspectedCauses,
        List<RecommendedActionResponse> recommendedActions,
        List<String> evidence,
        List<String> warnings
) {
}
