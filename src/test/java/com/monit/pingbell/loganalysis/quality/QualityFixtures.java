package com.monit.pingbell.loganalysis.quality;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Shared loader for the synthetic quality fixtures (scenarios.json + *.log) - used by both
 * LogAnalysisQualityFixtureTest (fixture shape/safety checks) and the RAG quality evaluation
 * (LogAnalysisQualityScorerTest, RagQualityEvaluationTest - Issue 6). */
final class QualityFixtures {

    private static final String RESOURCE_ROOT = "loganalysis/quality/";

    private QualityFixtures() {
    }

    static List<QualityScenario> scenarios() throws IOException {
        try (InputStream input = requiredResource("scenarios.json")) {
            return new ObjectMapper().readValue(input, new TypeReference<>() {
            });
        }
    }

    static String resource(String name) throws IOException {
        try (InputStream input = requiredResource(name)) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static InputStream requiredResource(String name) {
        InputStream input = QualityFixtures.class.getClassLoader().getResourceAsStream(RESOURCE_ROOT + name);
        if (input == null) {
            throw new IllegalStateException("Missing quality resource: " + RESOURCE_ROOT + name);
        }
        return input;
    }
}
