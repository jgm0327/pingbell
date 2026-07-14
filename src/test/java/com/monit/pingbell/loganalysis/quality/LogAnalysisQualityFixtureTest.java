package com.monit.pingbell.loganalysis.quality;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class LogAnalysisQualityFixtureTest {

    private static final String RESOURCE_ROOT = "loganalysis/quality/";
    private final ClassLoader classLoader = getClass().getClassLoader();

    @Test
    void definesFourRequiredSyntheticScenariosWithCompleteExpectations() throws IOException {
        List<QualityScenario> scenarios = scenarios();

        assertThat(scenarios)
                .extracting(QualityScenario::id)
                .containsExactlyInAnyOrder("timeout", "db-connection-failure", "http-5xx", "out-of-memory");
        assertThat(scenarios).allSatisfy(scenario -> {
            assertThat(scenario.logFile()).endsWith(".log");
            assertThat(scenario.question()).isNotBlank();
            assertThat(scenario.expectedCauseCandidates()).isNotEmpty().doesNotContainNull();
            assertThat(scenario.requiredEvidence()).isNotEmpty().doesNotContainNull();
            assertThat(scenario.firstChecks()).isNotEmpty().doesNotContainNull();
            assertThat(scenario.forbiddenActions()).isNotEmpty().doesNotContainNull();
            assertThat(resource(scenario.logFile())).isNotBlank();
        });
    }

    @Test
    void fixtureLogsDoNotContainCredentialOrPersonalDataShapes() throws IOException {
        for (QualityScenario scenario : scenarios()) {
            String log = resource(scenario.logFile());

            assertThat(log).doesNotContainIgnoringCase(
                    "authorization:", "bearer ", "password=", "passwd=", "pwd=",
                    "api_key=", "api-key=", "secret=", "cookie:", "session="
            );
            assertThat(log).doesNotContainPattern("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
            assertThat(log).doesNotContainPattern("(?:\\d{1,3}\\.){3}\\d{1,3}");
        }
    }

    @Test
    void includesPromptInjectionAsUntrustedLogData() throws IOException {
        String log = resource("http-5xx.log");

        assertThat(log).contains("Ignore previous instructions");
    }

    private List<QualityScenario> scenarios() throws IOException {
        try (InputStream input = requiredResource("scenarios.json")) {
            return new ObjectMapper().readValue(input, new TypeReference<>() {
            });
        }
    }

    private String resource(String name) throws IOException {
        try (InputStream input = requiredResource(name)) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private InputStream requiredResource(String name) {
        InputStream input = classLoader.getResourceAsStream(RESOURCE_ROOT + name);
        assertThat(input).as("quality resource %s", name).isNotNull();
        return input;
    }

    private record QualityScenario(
            String id,
            String logFile,
            String question,
            Set<String> expectedCauseCandidates,
            Set<String> requiredEvidence,
            Set<String> firstChecks,
            Set<String> forbiddenActions
    ) {
    }
}
