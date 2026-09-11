package com.monit.pingbell.loganalysis.quality;

import java.util.Set;

/** One synthetic incident scenario used to evaluate log analysis quality (Issue 6,
 * docs/ai/rag-implementation-issues.md). Loaded from
 * src/test/resources/loganalysis/quality/scenarios.json - never real customer data. */
record QualityScenario(
        String id,
        String logFile,
        String question,
        Set<String> expectedCauseCandidates,
        Set<String> requiredEvidence,
        Set<String> firstChecks,
        Set<String> forbiddenActions
) {
}
